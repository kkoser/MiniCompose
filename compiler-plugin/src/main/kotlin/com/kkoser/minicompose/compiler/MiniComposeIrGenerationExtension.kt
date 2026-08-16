package com.kkoser.minicompose.compiler

import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.backend.common.IrElementTransformerVoidWithContext
import org.jetbrains.kotlin.backend.common.lower.DeclarationIrBuilder
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSeverity
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.ir.IrStatement
import org.jetbrains.kotlin.ir.builders.irCall
import org.jetbrains.kotlin.ir.builders.irBlock
import org.jetbrains.kotlin.ir.builders.irGet
import org.jetbrains.kotlin.ir.builders.irIfThen
import org.jetbrains.kotlin.ir.builders.irTemporary
import org.jetbrains.kotlin.ir.builders.irTry
import org.jetbrains.kotlin.ir.builders.irVararg
import org.jetbrains.kotlin.ir.declarations.IrModuleFragment
import org.jetbrains.kotlin.ir.declarations.IrSimpleFunction
import org.jetbrains.kotlin.ir.expressions.IrCall
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.symbols.UnsafeDuringIrConstructionAPI
import org.jetbrains.kotlin.ir.types.isUnit
import org.jetbrains.kotlin.ir.util.fqNameWhenAvailable
import org.jetbrains.kotlin.ir.util.hasAnnotation
import org.jetbrains.kotlin.ir.util.isFakeOverride
import org.jetbrains.kotlin.ir.visitors.IrElementTransformerVoid
import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name

private val MINI_COMPOSABLE_FQ_NAME = FqName("com.kkoser.minicompose.annotations.MiniComposable")
private val RUNTIME_FQ_NAME = "com.kkoser.minicompose.runtime"

class MiniComposeIrGenerationExtension(
    private val messageCollector: MessageCollector
) : IrGenerationExtension {
    override fun generate(moduleFragment: IrModuleFragment, pluginContext: IrPluginContext) {
        val symbols = MiniComposeSymbols(pluginContext)
        moduleFragment.transform(MiniComposeValidationTransformer(messageCollector), null)
        moduleFragment.transform(MiniComposeCallLowering(symbols, pluginContext), null)
    }
}

private class MiniComposeValidationTransformer(
    private val messageCollector: MessageCollector
) : IrElementTransformerVoid() {
    override fun visitSimpleFunction(declaration: IrSimpleFunction): IrStatement {
        super.visitSimpleFunction(declaration)

        if (!declaration.hasAnnotation(MINI_COMPOSABLE_FQ_NAME)) {
            return declaration
        }

        if (!declaration.returnType.isUnit()) {
            report(declaration, "@MiniComposable functions must return Unit")
        }
        if (declaration.isSuspend) {
            report(declaration, "@MiniComposable functions do not support suspend")
        }
        if (declaration.isInline) {
            report(declaration, "@MiniComposable functions do not support inline")
        }
        if (declaration.typeParameters.isNotEmpty()) {
            report(declaration, "@MiniComposable functions do not support type parameters")
        }
        if (declaration.isFakeOverride || declaration.overriddenSymbols.isNotEmpty()) {
            report(declaration, "@MiniComposable functions do not support overrides")
        }

        return declaration
    }

    private fun report(function: IrSimpleFunction, message: String) {
        val functionName = function.fqNameWhenAvailable?.asString() ?: function.name.asString()
        messageCollector.report(
            CompilerMessageSeverity.ERROR,
            "$message: $functionName"
        )
    }
}

private class MiniComposeCallLowering(
    private val symbols: MiniComposeSymbols,
    private val pluginContext: IrPluginContext
) : IrElementTransformerVoidWithContext() {
    @OptIn(UnsafeDuringIrConstructionAPI::class)
    override fun visitCall(expression: IrCall): IrExpression {
        expression.transformChildren(this, null)

        if (expression.symbol.owner.hasAnnotation(MINI_COMPOSABLE_FQ_NAME)) {
            return rewriteComposableCall(expression)
        }

        val replacementSymbol = when (expression.symbol) {
            symbols.sourceText -> symbols.emitText
            symbols.sourceButton -> symbols.emitButton
            symbols.sourceColumn -> symbols.emitColumn
            symbols.sourceRow -> symbols.emitRow
            symbols.sourceRemember -> symbols.emitRemember
            symbols.sourceKey -> symbols.emitKey
            else -> null
        } ?: return expression

        return rewriteCall(expression, replacementSymbol)
    }

    @OptIn(UnsafeDuringIrConstructionAPI::class)
    private fun rewriteComposableCall(source: IrCall): IrExpression {
        val builder = DeclarationIrBuilder(
            pluginContext,
            currentScope!!.scope.scopeOwnerSymbol,
            source.startOffset,
            source.endOffset
        )

        return builder.irBlock(resultType = source.type) {
            val inputs = mutableListOf<IrExpression>()
            val dispatchReceiverVariable = source.dispatchReceiver?.let { receiver ->
                irTemporary(receiver, "miniComposeDispatchReceiver").also { temporary ->
                    inputs += builder.irGet(temporary)
                }
            }
            val extensionReceiverVariable = source.extensionReceiver?.let { receiver ->
                irTemporary(receiver, "miniComposeExtensionReceiver").also { temporary ->
                    inputs += builder.irGet(temporary)
                }
            }
            val arguments = mutableMapOf<Int, org.jetbrains.kotlin.ir.declarations.IrVariable>()
            source.symbol.owner.valueParameters.indices
                .mapNotNull { index -> source.getValueArgument(index)?.let { index to it } }
                .sortedWith(compareBy({ (_, argument) -> argument.startOffset }, { (index, _) -> index }))
                .forEach { (index, argument) ->
                    val temporary = irTemporary(argument, "miniComposeArgument$index")
                    arguments[index] = temporary
                    inputs += builder.irGet(temporary)
                }

            val originalCall = builder.irCall(source.symbol, source.type).apply {
                this.dispatchReceiver = dispatchReceiverVariable?.let(builder::irGet)
                this.extensionReceiver = extensionReceiverVariable?.let(builder::irGet)
                for (index in 0 until source.typeArgumentsCount) {
                    putTypeArgument(index, source.getTypeArgument(index))
                }
                source.symbol.owner.valueParameters.indices.forEach { index ->
                    putValueArgument(index, arguments[index]?.let(builder::irGet))
                }
            }

            val composer = builder.irCall(symbols.currentComposer)
            val shouldCompose = builder.irCall(symbols.beginComposableCall).apply {
                putValueArgument(0, composer)
                putValueArgument(1, builder.irVararg(pluginContext.irBuiltIns.anyNType, inputs))
            }
            +builder.irIfThen(pluginContext.irBuiltIns.unitType, shouldCompose, builder.irBlock {
                val endCall = builder.irCall(symbols.endComposableCall).apply {
                    putValueArgument(0, builder.irCall(symbols.currentComposer))
                }
                +builder.irTry(
                    pluginContext.irBuiltIns.unitType,
                    originalCall,
                    emptyList(),
                    endCall
                )
            })
        }
    }

    @OptIn(UnsafeDuringIrConstructionAPI::class)
    private fun rewriteCall(source: IrCall, target: org.jetbrains.kotlin.ir.symbols.IrSimpleFunctionSymbol): IrCall {
        val builder = DeclarationIrBuilder(pluginContext, currentScope!!.scope.scopeOwnerSymbol, source.startOffset, source.endOffset)
        return builder.irCall(target, source.type).apply {
            putValueArgument(0, builder.irCall(symbols.currentComposer))

            for (index in 0 until source.typeArgumentsCount) {
                putTypeArgument(index, source.getTypeArgument(index))
            }

            for (index in 0 until source.symbol.owner.valueParameters.size) {
                putValueArgument(index + 1, source.getValueArgument(index))
            }
        }
    }
}

private class MiniComposeSymbols(
    pluginContext: IrPluginContext
) {
    val currentComposer = referenceFunction(pluginContext, "currentComposer") { function ->
        function.valueParameters.isEmpty() &&
            function.extensionReceiverParameter == null &&
            function.dispatchReceiverParameter == null
    }

    val sourceText = referenceFunction(pluginContext, "text") { function ->
        function.extensionReceiverParameter == null && function.valueParameters.size == 1
    }
    val sourceButton = referenceFunction(pluginContext, "button") { function ->
        function.extensionReceiverParameter == null && function.valueParameters.size == 2
    }
    val sourceColumn = referenceFunction(pluginContext, "column") { function ->
        function.extensionReceiverParameter == null && function.valueParameters.size == 2
    }
    val sourceRow = referenceFunction(pluginContext, "row") { function ->
        function.extensionReceiverParameter == null && function.valueParameters.size == 2
    }
    val sourceRemember = referenceFunction(pluginContext, "remember") { function ->
        function.extensionReceiverParameter == null && function.valueParameters.size == 1
    }
    val sourceKey = referenceFunction(pluginContext, "key") { function ->
        function.extensionReceiverParameter == null &&
            function.valueParameters.size == 2 &&
            function.valueParameters[0].varargElementType != null
    }

    val emitText = referenceFunction(pluginContext, "emitText") { function ->
        function.valueParameters.size == 2
    }
    val emitButton = referenceFunction(pluginContext, "emitButton") { function ->
        function.valueParameters.size == 3
    }
    val emitColumn = referenceFunction(pluginContext, "emitColumn") { function ->
        function.valueParameters.size == 3
    }
    val emitRow = referenceFunction(pluginContext, "emitRow") { function ->
        function.valueParameters.size == 3
    }
    val emitRemember = referenceFunction(pluginContext, "emitRemember") { function ->
        function.valueParameters.size == 2
    }
    val emitKey = referenceFunction(pluginContext, "emitKey") { function ->
        function.valueParameters.size == 3 && function.valueParameters[1].varargElementType != null
    }
    val beginComposableCall = referenceFunction(pluginContext, "beginComposableCall") { function ->
        function.valueParameters.size == 2
    }
    val endComposableCall = referenceFunction(pluginContext, "endComposableCall") { function ->
        function.valueParameters.size == 1
    }

    @OptIn(UnsafeDuringIrConstructionAPI::class)
    private fun referenceFunction(
        pluginContext: IrPluginContext,
        name: String,
        predicate: (IrSimpleFunction) -> Boolean
    ) = pluginContext.referenceFunctions(CallableId(FqName(RUNTIME_FQ_NAME), Name.identifier(name))).single { symbol ->
        predicate(symbol.owner)
    }
}
