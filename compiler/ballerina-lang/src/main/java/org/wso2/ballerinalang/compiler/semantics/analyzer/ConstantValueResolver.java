/*
 *  Copyright (c) 2019, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 *  WSO2 Inc. licenses this file to you under the Apache License,
 *  Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 */

package org.wso2.ballerinalang.compiler.semantics.analyzer;

import io.ballerina.tools.diagnostics.Location;
import org.ballerinalang.model.TreeBuilder;
import org.ballerinalang.model.elements.Flag;
import org.ballerinalang.model.elements.PackageID;
import org.ballerinalang.model.symbols.SymbolKind;
import org.ballerinalang.model.tree.NodeKind;
import org.ballerinalang.model.tree.OperatorKind;
import org.ballerinalang.model.tree.expressions.RecordLiteralNode;
import org.ballerinalang.model.types.TypeKind;
import org.ballerinalang.util.diagnostic.DiagnosticErrorCode;
import org.wso2.ballerinalang.compiler.desugar.ASTBuilderUtil;
import org.wso2.ballerinalang.compiler.diagnostic.BLangDiagnosticLog;
import org.wso2.ballerinalang.compiler.parser.BLangAnonymousModelHelper;
import org.wso2.ballerinalang.compiler.semantics.model.Scope;
import org.wso2.ballerinalang.compiler.semantics.model.SymbolEnv;
import org.wso2.ballerinalang.compiler.semantics.model.SymbolTable;
import org.wso2.ballerinalang.compiler.semantics.model.symbols.*;
import org.wso2.ballerinalang.compiler.semantics.model.types.*;
import org.wso2.ballerinalang.compiler.tree.*;
import org.wso2.ballerinalang.compiler.tree.expressions.BLangBinaryExpr;
import org.wso2.ballerinalang.compiler.tree.expressions.BLangConstRef;
import org.wso2.ballerinalang.compiler.tree.expressions.BLangConstant;
import org.wso2.ballerinalang.compiler.tree.expressions.BLangExpression;
import org.wso2.ballerinalang.compiler.tree.expressions.BLangGroupExpr;
import org.wso2.ballerinalang.compiler.tree.expressions.BLangLiteral;
import org.wso2.ballerinalang.compiler.tree.expressions.BLangNumericLiteral;
import org.wso2.ballerinalang.compiler.tree.expressions.BLangRecordLiteral;
import org.wso2.ballerinalang.compiler.tree.expressions.BLangSimpleVarRef;
import org.wso2.ballerinalang.compiler.tree.expressions.BLangUnaryExpr;
import org.wso2.ballerinalang.compiler.tree.types.BLangFiniteTypeNode;
import org.wso2.ballerinalang.compiler.tree.types.BLangRecordTypeNode;
import org.wso2.ballerinalang.compiler.tree.types.BLangStructureTypeNode;
import org.wso2.ballerinalang.compiler.tree.types.BLangUserDefinedType;
import org.wso2.ballerinalang.compiler.util.*;
import org.wso2.ballerinalang.util.Flags;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.*;
import java.util.function.BiFunction;

import static org.ballerinalang.model.symbols.SymbolOrigin.SOURCE;
import static org.ballerinalang.model.symbols.SymbolOrigin.VIRTUAL;

/**
 * @since 0.990.4
 */
public class ConstantValueResolver extends BLangNodeVisitor {

    private static final CompilerContext.Key<ConstantValueResolver> CONSTANT_VALUE_RESOLVER_KEY =
            new CompilerContext.Key<>();
    private BConstantSymbol currentConstSymbol;
    private BLangConstantValue result;
    private BLangDiagnosticLog dlog;
    private Location currentPos;
    private Map<BConstantSymbol, BLangConstant> unresolvedConstants = new HashMap<>();
    private Map<String, BLangConstantValue> constantMap = new HashMap<String, BLangConstantValue>();
    private ArrayList<BLangTypeDefinition> typeDefinition;
    private SymbolEnv symEnv;
    private BLangAnonymousModelHelper anonymousModelHelper;
    private Names names;
    private SymbolTable symTable;
    private Types types;
    private BLangRecordTypeNode recordTypeNode;
    private PackageID pkgID;

    private ConstantValueResolver(CompilerContext context) {
        context.put(CONSTANT_VALUE_RESOLVER_KEY, this);
        this.dlog = BLangDiagnosticLog.getInstance(context);
    }

    public static ConstantValueResolver getInstance(CompilerContext context) {
        ConstantValueResolver constantValueResolver = context.get(CONSTANT_VALUE_RESOLVER_KEY);
        if (constantValueResolver == null) {
            constantValueResolver = new ConstantValueResolver(context);
        }
        return constantValueResolver;
    }

    public void resolve(List<BLangConstant> constants, PackageID packageID, SymbolEnv symEnv, SymbolTable symTable,
                        BLangAnonymousModelHelper anonymousModelHelper, ArrayList<BLangTypeDefinition> typeDefinition,
                        Names names, Types types) {
        this.dlog.setCurrentPackageId(packageID);
        this.pkgID = packageID;
        this.typeDefinition = typeDefinition;
        this.symEnv = symEnv;
        this.symTable = symTable;
        this.names = names;
        this.anonymousModelHelper = anonymousModelHelper;
        this.types = types;
        constants.forEach(constant -> this.unresolvedConstants.put(constant.symbol, constant));
        constants.forEach(constant -> constant.accept(this));
        constantMap.clear();
        constants.forEach(constant -> checkUniqueness(constant));
        constants.forEach(constant -> updateSymbolType(constant));
    }

    @Override
    public void visit(BLangConstant constant) {
        BConstantSymbol tempCurrentConstSymbol = this.currentConstSymbol;
        this.currentConstSymbol = constant.symbol;
        this.currentConstSymbol.value = visitExpr(constant.expr);
        unresolvedConstants.remove(this.currentConstSymbol);
        this.currentConstSymbol = tempCurrentConstSymbol;
    }

    @Override
    public void visit(BLangLiteral literal) {
        this.result = new BLangConstantValue(literal.value, literal.getBType());
    }

    @Override
    public void visit(BLangNumericLiteral literal) {
        this.result = new BLangConstantValue(literal.value, literal.getBType());
    }

    @Override
    public void visit(BLangConstRef constRef) {
        this.result = ((BConstantSymbol) constRef.symbol).value;
    }

    @Override
    public void visit(BLangSimpleVarRef varRef) {
        if (varRef.symbol == null || (varRef.symbol.tag & SymTag.CONSTANT) != SymTag.CONSTANT) {
            this.result = null;
            return;
        }

        BConstantSymbol constSymbol = (BConstantSymbol) varRef.symbol;
        BLangConstantValue constVal = constSymbol.value;
        if (constVal != null) {
            this.result = constVal;
            return;
        }

        if (this.currentConstSymbol == constSymbol) {
            dlog.error(varRef.pos, DiagnosticErrorCode.SELF_REFERENCE_CONSTANT, constSymbol.name);
            return;
        }

        if (!this.unresolvedConstants.containsKey(constSymbol)) {
            dlog.error(varRef.pos, DiagnosticErrorCode.CANNOT_RESOLVE_CONST, constSymbol.name.value);
            this.result = null;
            return;
        }

        // if the referring constant is not yet resolved, then go and resolve it first.
        this.unresolvedConstants.get(constSymbol).accept(this);
        this.result = constSymbol.value;
    }

    @Override
    public void visit(BLangRecordLiteral recordLiteral) {
        Map<String, BLangConstantValue> mapConstVal = new HashMap<>();
        for (RecordLiteralNode.RecordField field : recordLiteral.fields) {
            String key;
            BLangConstantValue value;

            if (field.isKeyValueField()) {
                BLangRecordLiteral.BLangRecordKeyValueField keyValuePair =
                        (BLangRecordLiteral.BLangRecordKeyValueField) field;
                NodeKind nodeKind = keyValuePair.key.expr.getKind();

                if (nodeKind == NodeKind.LITERAL || nodeKind == NodeKind.NUMERIC_LITERAL) {
                    key = (String) ((BLangLiteral) keyValuePair.key.expr).value;
                } else if (nodeKind == NodeKind.SIMPLE_VARIABLE_REF) {
                    key = ((BLangSimpleVarRef) keyValuePair.key.expr).variableName.value;
                } else {
                    continue;
                }

                value = visitExpr(keyValuePair.valueExpr);
            } else if (field.getKind() == NodeKind.SIMPLE_VARIABLE_REF) {
                BLangRecordLiteral.BLangRecordVarNameField varNameField =
                        (BLangRecordLiteral.BLangRecordVarNameField) field;
                key = varNameField.variableName.value;
                value = visitExpr(varNameField);
            } else {
                BLangConstantValue spreadOpConstValue =
                        visitExpr(((BLangRecordLiteral.BLangRecordSpreadOperatorField) field).expr);

                if (spreadOpConstValue != null) {
                    mapConstVal.putAll((Map<String, BLangConstantValue>) spreadOpConstValue.value);
                }
                continue;
            }

            mapConstVal.put(key, value);
        }

        this.result = new BLangConstantValue(mapConstVal, recordLiteral.getBType());
    }

    @Override
    public void visit(BLangBinaryExpr binaryExpr) {
        BLangConstantValue lhs = visitExpr(binaryExpr.lhsExpr);
        BLangConstantValue rhs = visitExpr(binaryExpr.rhsExpr);
        this.result = calculateConstValue(lhs, rhs, binaryExpr.opKind);
    }

    public void visit(BLangGroupExpr groupExpr) {
        this.result = visitExpr(groupExpr.expression);
    }

    public void visit(BLangUnaryExpr unaryExpr) {
        BLangConstantValue value = visitExpr(unaryExpr.expr);
        this.result = evaluateUnaryOperator(value, unaryExpr.operator);
    }

    private BLangConstantValue calculateConstValue(BLangConstantValue lhs, BLangConstantValue rhs, OperatorKind kind) {
        if (lhs == null || rhs == null || lhs.value == null || rhs.value == null) {
            // This is a compilation error.
            // This is to avoid NPE exceptions in sub-sequent validations.
            return new BLangConstantValue(null, this.currentConstSymbol.type);
        }
        // See Types.isAllowedConstantType() for supported types.
        try {
            switch (kind) {
                case ADD:
                    return calculateAddition(lhs, rhs);
                case SUB:
                    return calculateSubtract(lhs, rhs);
                case MUL:
                    return calculateMultiplication(lhs, rhs);
                case DIV:
                    return calculateDivision(lhs, rhs);
                case BITWISE_AND:
                    return calculateBitWiseOp(lhs, rhs, (a, b) -> a & b);
                case BITWISE_OR:
                    return calculateBitWiseOp(lhs, rhs, (a, b) -> a | b);
                case BITWISE_LEFT_SHIFT:
                    return calculateBitWiseOp(lhs, rhs, (a, b) -> a << b);
                case BITWISE_RIGHT_SHIFT:
                    return calculateBitWiseOp(lhs, rhs, (a, b) -> a >> b);
                case BITWISE_UNSIGNED_RIGHT_SHIFT:
                    return calculateBitWiseOp(lhs, rhs, (a, b) -> a >>> b);
                case BITWISE_XOR:
                    return calculateBitWiseOp(lhs, rhs, (a, b) -> a ^ b);
                default:
                    dlog.error(currentPos, DiagnosticErrorCode.CONSTANT_EXPRESSION_NOT_SUPPORTED);
            }
        } catch (NumberFormatException nfe) {
            // Ignore. This will be handled as a compiler error.
        } catch (ArithmeticException ae) {
            dlog.error(currentPos, DiagnosticErrorCode.INVALID_CONST_EXPRESSION, ae.getMessage());
        }
        // This is a compilation error already logged.
        // This is to avoid NPE exceptions in sub-sequent validations.
        return new BLangConstantValue(null, this.currentConstSymbol.type);
    }

    private BLangConstantValue evaluateUnaryOperator(BLangConstantValue value, OperatorKind kind) {
        if (value == null || value.value == null) {
            // This is a compilation error.
            // This is to avoid NPE exceptions in sub-sequent validations.
            return new BLangConstantValue(null, this.currentConstSymbol.type);
        }
        
        try {
            switch (kind) {
                case ADD:
                    return new BLangConstantValue(value.value, currentConstSymbol.type);
                case SUB:
                    return calculateNegation(value);
                case BITWISE_COMPLEMENT:
                    return calculateBitWiseComplement(value);
                case NOT:
                    return calculateBooleanComplement(value);
            }
        } catch (ClassCastException ce) {
            // Ignore. This will be handled as a compiler error.
        }
        // This is a compilation error already logged.
        // This is to avoid NPE exceptions in sub-sequent validations.
        return new BLangConstantValue(null, this.currentConstSymbol.type);
    }

    private BLangConstantValue calculateBitWiseOp(BLangConstantValue lhs, BLangConstantValue rhs,
                                                  BiFunction<Long, Long, Long> func) {
        switch (this.currentConstSymbol.type.tag) {
            case TypeTags.INT:
                Long val = func.apply((Long) lhs.value, (Long) rhs.value);
                return new BLangConstantValue(val, this.currentConstSymbol.type);
            default:
                dlog.error(currentPos, DiagnosticErrorCode.CONSTANT_EXPRESSION_NOT_SUPPORTED);

        }
        return new BLangConstantValue(null, this.currentConstSymbol.type);
    }

    private BLangConstantValue calculateAddition(BLangConstantValue lhs, BLangConstantValue rhs) {
        // TODO : Handle overflow in numeric values.
        Object result = null;
        switch (this.currentConstSymbol.type.tag) {
            case TypeTags.INT:
            case TypeTags.BYTE: // Byte will be a compiler error.
                result = (Long) lhs.value + (Long) rhs.value;
                break;
            case TypeTags.FLOAT:
                result = String.valueOf(Double.parseDouble(String.valueOf(lhs.value))
                        + Double.parseDouble(String.valueOf(rhs.value)));
                break;
            case TypeTags.DECIMAL:
                BigDecimal lhsDecimal = new BigDecimal(String.valueOf(lhs.value), MathContext.DECIMAL128);
                BigDecimal rhsDecimal = new BigDecimal(String.valueOf(rhs.value), MathContext.DECIMAL128);
                BigDecimal resultDecimal = lhsDecimal.add(rhsDecimal, MathContext.DECIMAL128);
                result = resultDecimal.toPlainString();
                break;
            case TypeTags.STRING:
                result = String.valueOf(lhs.value) + String.valueOf(rhs.value);
                break;
        }
        return new BLangConstantValue(result, currentConstSymbol.type);
    }

    private BLangConstantValue calculateSubtract(BLangConstantValue lhs, BLangConstantValue rhs) {
        Object result = null;
        switch (this.currentConstSymbol.type.tag) {
            case TypeTags.INT:
            case TypeTags.BYTE: // Byte will be a compiler error.
                result = (Long) lhs.value - (Long) rhs.value;
                break;
            case TypeTags.FLOAT:
                result = String.valueOf(Double.parseDouble(String.valueOf(lhs.value))
                        - Double.parseDouble(String.valueOf(rhs.value)));
                break;
            case TypeTags.DECIMAL:
                BigDecimal lhsDecimal = new BigDecimal(String.valueOf(lhs.value), MathContext.DECIMAL128);
                BigDecimal rhsDecimal = new BigDecimal(String.valueOf(rhs.value), MathContext.DECIMAL128);
                BigDecimal resultDecimal = lhsDecimal.subtract(rhsDecimal, MathContext.DECIMAL128);
                result = resultDecimal.toPlainString();
                break;
        }
        return new BLangConstantValue(result, currentConstSymbol.type);
    }

    private BLangConstantValue calculateMultiplication(BLangConstantValue lhs, BLangConstantValue rhs) {
        // TODO : Handle overflow in numeric values.
        Object result = null;
        switch (this.currentConstSymbol.type.tag) {
            case TypeTags.INT:
            case TypeTags.BYTE: // Byte will be a compiler error.
                result = (Long) lhs.value * (Long) rhs.value;
                break;
            case TypeTags.FLOAT:
                result = String.valueOf(Double.parseDouble(String.valueOf(lhs.value))
                        * Double.parseDouble(String.valueOf(rhs.value)));
                break;
            case TypeTags.DECIMAL:
                BigDecimal lhsDecimal = new BigDecimal(String.valueOf(lhs.value), MathContext.DECIMAL128);
                BigDecimal rhsDecimal = new BigDecimal(String.valueOf(rhs.value), MathContext.DECIMAL128);
                BigDecimal resultDecimal = lhsDecimal.multiply(rhsDecimal, MathContext.DECIMAL128);
                result = resultDecimal.toPlainString();
                break;
        }
        return new BLangConstantValue(result, currentConstSymbol.type);
    }

    private BLangConstantValue calculateDivision(BLangConstantValue lhs, BLangConstantValue rhs) {
        Object result = null;
        switch (this.currentConstSymbol.type.tag) {
            case TypeTags.INT:
            case TypeTags.BYTE: // Byte will be a compiler error.
                result = (Long) ((Long) lhs.value / (Long) rhs.value);
                break;
            case TypeTags.FLOAT:
                result = String.valueOf(Double.parseDouble(String.valueOf(lhs.value))
                        / Double.parseDouble(String.valueOf(rhs.value)));
                break;
            case TypeTags.DECIMAL:
                BigDecimal lhsDecimal = new BigDecimal(String.valueOf(lhs.value), MathContext.DECIMAL128);
                BigDecimal rhsDecimal = new BigDecimal(String.valueOf(rhs.value), MathContext.DECIMAL128);
                BigDecimal resultDecimal = lhsDecimal.divide(rhsDecimal, MathContext.DECIMAL128);
                result = resultDecimal.toPlainString();
                break;
        }
        return new BLangConstantValue(result, currentConstSymbol.type);
    }

    // TODO : Not working at the moment.
    private BLangConstantValue calculateMod(BLangConstantValue lhs, BLangConstantValue rhs) {
        Object result = null;
        switch (this.currentConstSymbol.type.tag) {
            case TypeTags.INT:
            case TypeTags.BYTE: // Byte will be a compiler error.
                result = (Long) ((Long) lhs.value % (Long) rhs.value);
                break;
            case TypeTags.FLOAT:
                result = String.valueOf(Double.parseDouble(String.valueOf(lhs.value))
                        % Double.parseDouble(String.valueOf(rhs.value)));
                break;
            case TypeTags.DECIMAL:
                BigDecimal lhsDecimal = new BigDecimal(String.valueOf(lhs.value), MathContext.DECIMAL128);
                BigDecimal rhsDecimal = new BigDecimal(String.valueOf(rhs.value), MathContext.DECIMAL128);
                BigDecimal resultDecimal = lhsDecimal.remainder(rhsDecimal, MathContext.DECIMAL128);
                result = resultDecimal.toPlainString();
                break;
        }
        return new BLangConstantValue(result, currentConstSymbol.type);
    }

    private BLangConstantValue calculateNegation(BLangConstantValue value) {
        Object result = null;
        switch (this.currentConstSymbol.type.tag) {
            case TypeTags.INT:
                result = -1 * ((Long) (value.value));
                break;
            case TypeTags.FLOAT:
                result = String.valueOf(-1 * Double.parseDouble(String.valueOf(value.value)));
                break;
            case TypeTags.DECIMAL:
                BigDecimal valDecimal = new BigDecimal(String.valueOf(value.value), MathContext.DECIMAL128);
                BigDecimal negDecimal = new BigDecimal(String.valueOf(-1), MathContext.DECIMAL128);
                BigDecimal resultDecimal = valDecimal.multiply(negDecimal, MathContext.DECIMAL128);
                result = resultDecimal.toPlainString();
                break;
        }
        return new BLangConstantValue(result, currentConstSymbol.type);
    }

    private BLangConstantValue calculateBitWiseComplement(BLangConstantValue value) {
        Object result = null;
        if (this.currentConstSymbol.type.tag == TypeTags.INT) {
            result = ~((Long) (value.value));
        }
        return new BLangConstantValue(result, currentConstSymbol.type);
    }

    private BLangConstantValue calculateBooleanComplement(BLangConstantValue value) {
        Object result = null;
        if (this.currentConstSymbol.type.tag == TypeTags.BOOLEAN) {
            result = !((Boolean) (value.value));
        }
        return new BLangConstantValue(result, currentConstSymbol.type);
    }

    private BLangConstantValue visitExpr(BLangExpression node) {
        if (!node.typeChecked) {
            return null;
        }
        switch (node.getKind()) {
            case LITERAL:
            case NUMERIC_LITERAL:
            case RECORD_LITERAL_EXPR:
            case SIMPLE_VARIABLE_REF:
            case BINARY_EXPR:
            case GROUP_EXPR:
            case UNARY_EXPR:
                BLangConstantValue prevResult = this.result;
                Location prevPos = this.currentPos;
                this.currentPos = node.pos;
                this.result = null;
                node.accept(this);
                BLangConstantValue newResult = this.result;
                this.result = prevResult;
                this.currentPos = prevPos;
                return newResult;
            default:
                return null;
        }
    }

    private void checkUniqueness(BLangConstant constant) {
        if (constant.symbol.kind == SymbolKind.CONSTANT) {
            String nameString = constant.name.value;
            BLangConstantValue value = constant.symbol.value;

            if (constantMap.containsKey(nameString)) {
                if (value == null) {
                    dlog.error(constant.name.pos, DiagnosticErrorCode.ALREADY_INITIALIZED_SYMBOL, nameString);
                } else {
                    BLangConstantValue lastValue = constantMap.get(nameString);
                    if (!value.equals(lastValue)) {
                        if (lastValue == null) {
                            dlog.error(constant.name.pos, DiagnosticErrorCode.ALREADY_INITIALIZED_SYMBOL, nameString);
                        } else {
                            dlog.error(constant.name.pos, DiagnosticErrorCode.ALREADY_INITIALIZED_SYMBOL_WITH_ANOTHER,
                                    nameString, lastValue);
                        }
                    }
                }
            } else {
                constantMap.put(nameString, value);
            }
        }
    }

    private void updateSymbolType(BLangConstant constant) {
        if (constant.symbol.kind == SymbolKind.CONSTANT && constant.symbol.type.getKind() != TypeKind.FINITE &&
                constant.symbol.value != null) {
            BType singletonType = checkType(constant.name.value, constant, constant.symbol.value.value,
                    constant.symbol.type, constant.symbol.pos);
            if (singletonType != null) {
                constant.symbol.type = singletonType;
            }
        }
    }

    public BFiniteType createFiniteType(BLangConstant constant, BLangExpression expr) {
        BTypeSymbol finiteTypeSymbol = Symbols.createTypeSymbol(SymTag.FINITE_TYPE,
                Flags.asMask(EnumSet.noneOf(Flag.class)), Names.EMPTY, constant.symbol.pkgID, null,
                constant.symbol.owner, constant.symbol.pos, VIRTUAL);
        BFiniteType finiteType = new BFiniteType(finiteTypeSymbol);
        finiteType.addValue(expr);
        return finiteType;
    }

    private BType checkType(String name, BLangConstant constant, Object value, BType type, Location pos) {
        switch (type.getKind()) {
            case INT:
            case BYTE:
            case FLOAT:
            case DECIMAL:
                return createFiniteType(constant, createConstantNumericLiteralExpression(value, type, pos));
            case STRING:
            case NIL:
            case BOOLEAN:
                return createFiniteType(constant, createConstantLiteralExpression(value, type, pos));
            case MAP:
                if (value != null) {
                    return createRecordType(name, constant, value, type, pos);
                }
                return null;
            default:
                return null;
        }
    }

    private BLangNumericLiteral createConstantNumericLiteralExpression(Object value, BType type, Location pos) {
        BLangNumericLiteral literal = (BLangNumericLiteral) TreeBuilder.createNumericLiteralExpression();
        literal.value = value;
        literal.isConstant = true;
        literal.setBType(type);
        literal.pos = pos;
        return literal;
    }

    private BLangLiteral createConstantLiteralExpression(Object value, BType type, Location pos) {
        BLangLiteral literal = (BLangLiteral) TreeBuilder.createLiteralExpression();
        literal.value = value;
        literal.isConstant = true;
        literal.setBType(type);
        literal.pos = pos;
        return literal;
    }

    private BIntersectionType createRecordType(String name, BLangConstant constant, Object value, BType type,
                                               Location pos) {
        BRecordTypeSymbol recordTypeSymbol = new BRecordTypeSymbol(SymTag.RECORD, constant.symbol.flags,
                Names.fromString(name), constant.symbol.pkgID, null, constant.symbol.owner, pos, VIRTUAL);
        recordTypeSymbol.scope = constant.symbol.scope;
        BRecordType recordType = new BRecordType(recordTypeSymbol);
        recordType.sealed = true;
        recordType.flags = Flags.READONLY;
        recordType.restFieldType = new BNoType(TypeTags.NONE);
        recordTypeSymbol.type = recordType;
        for (String key : ((HashMap<String, Object>) value).keySet()) {
            BVarSymbol newSymbol = new BVarSymbol(constant.symbol.flags, Names.fromString(key), constant.symbol.pkgID, null,
                    constant.symbol.owner, pos, VIRTUAL);
            newSymbol.type = checkType(key, constant, ((BLangConstantValue) ((HashMap) value).get(key)).value,
                    ((BLangConstantValue) ((HashMap) value).get(key)).type, pos);
            BField field = new BField(Names.fromString(key), pos, newSymbol);
            field.symbol.flags |= Flags.REQUIRED;
            recordType.fields.put(key, field);
        }

        BIntersectionType intersectionType = ImmutableTypeCloner.defineImmutableRecordType(pos, recordType, recordType,
                this.symEnv, this.symTable, this.anonymousModelHelper, this.names, this.types, new HashSet<>());
        createTypeDefinition2(recordType, intersectionType, pos);
        return intersectionType;
    }

    private void createTypeDefinition(BRecordType originalType, BIntersectionType immutableType, Location pos) {
        BRecordTypeSymbol recordSymbol = Symbols.createRecordSymbol(originalType.tsymbol.flags, originalType.tsymbol.name,
                pkgID, null, symEnv.scope.owner, pos, VIRTUAL);

        BInvokableType bInvokableType = new BInvokableType(new ArrayList<>(), symTable.nilType, null);
        BInvokableSymbol initFuncSymbol = Symbols.createFunctionSymbol(
                Flags.PUBLIC, Names.EMPTY, Names.EMPTY, symEnv.enclPkg.symbol.pkgID, bInvokableType, symEnv.scope.owner,
                false, symTable.builtinPos, VIRTUAL);
        initFuncSymbol.retType = symTable.nilType;
        recordSymbol.initializerFunc = new BAttachedFunction(Names.INIT_FUNCTION_SUFFIX, initFuncSymbol,
                bInvokableType, symTable.builtinPos);

        recordSymbol.scope = new Scope(recordSymbol);
        recordSymbol.scope.define(
                names.fromString(recordSymbol.name.value + "." + recordSymbol.initializerFunc.funcName.value),
                recordSymbol.initializerFunc.symbol);

        BRecordType recordType = new BRecordType(recordSymbol, originalType.flags);

        recordType.immutableType = immutableType;
        recordSymbol.type = recordType;
        recordType.tsymbol = recordSymbol;

        BLangRecordTypeNode recordTypeNode = TypeDefBuilderHelper.createRecordTypeNode(new ArrayList<>(),
                recordType, pos);

        populateMutableStructureFields(symTable, names, recordTypeNode,
                recordType, originalType, pos, symEnv, pkgID);
        
        TypeDefBuilderHelper.createInitFunctionForRecordType(recordTypeNode, symEnv, names, symTable);
        BLangTypeDefinition typeDefinition = TypeDefBuilderHelper.addTypeDefinition(recordType, recordSymbol,
                recordTypeNode, symEnv);
        typeDefinition.pos = pos;
    }

    private void createTypeDefinition2(BRecordType originalType, BIntersectionType immutableType, Location pos) {
        BRecordTypeSymbol recordSymbol = Symbols.createRecordSymbol(originalType.tsymbol.flags, originalType.tsymbol.name,
                pkgID, null, symEnv.scope.owner, pos, VIRTUAL);

        BInvokableType bInvokableType = new BInvokableType(new ArrayList<>(), symTable.nilType, null);
        BInvokableSymbol initFuncSymbol = Symbols.createFunctionSymbol(
                Flags.PUBLIC, Names.EMPTY, Names.EMPTY, symEnv.enclPkg.symbol.pkgID, bInvokableType, symEnv.scope.owner,
                false, symTable.builtinPos, VIRTUAL);
        initFuncSymbol.retType = symTable.nilType;
        recordSymbol.initializerFunc = new BAttachedFunction(Names.INIT_FUNCTION_SUFFIX, initFuncSymbol,
                bInvokableType, symTable.builtinPos);

        recordSymbol.scope = new Scope(recordSymbol);
        recordSymbol.scope.define(
                names.fromString(recordSymbol.name.value + "." + recordSymbol.initializerFunc.funcName.value),
                recordSymbol.initializerFunc.symbol);




        BTypeDefinitionSymbol typeDefinitionSymbol = Symbols.createTypeDefinitionSymbol(originalType.tsymbol.flags,
                originalType.tsymbol.name, pkgID, null, symEnv.scope.owner, pos, VIRTUAL);

        typeDefinitionSymbol.scope = new Scope(typeDefinitionSymbol);
        typeDefinitionSymbol.scope.define(
                names.fromString(typeDefinitionSymbol.name.value), typeDefinitionSymbol);

        BRecordType recordType = new BRecordType(originalType.tsymbol, originalType.flags);

        recordType.immutableType = immutableType;
        recordSymbol.type = recordType;
        recordType.tsymbol = recordSymbol;
        typeDefinitionSymbol.type = recordType;

        BLangRecordTypeNode recordTypeNode = TypeDefBuilderHelper.createRecordTypeNode(new ArrayList<>(), recordType,
                pos);

        populateMutableStructureFields(symTable, names, recordTypeNode, recordType, originalType, pos, symEnv, pkgID);

        TypeDefBuilderHelper.createInitFunctionForRecordType(recordTypeNode, symEnv, names, symTable);
        BLangTypeDefinition typeDefinition = TypeDefBuilderHelper.createTypeDefinitionForTSymbol(recordType, typeDefinitionSymbol,
                recordTypeNode, symEnv);
        typeDefinition.pos = pos;
        typeDefinition.symbol.scope = new Scope(typeDefinition.symbol);
        typeDefinition.flagSet = new HashSet<>();
        typeDefinition.flagSet.add(Flag.PUBLIC);
        typeDefinition.flagSet.add(Flag.ANONYMOUS);
//        typeDefinition.symbol.scope.define(
//                names.fromString(typeDefinition.symbol.name.value), typeDefinition.symbol);
    }

    private void populateMutableStructureFields(SymbolTable symTable, Names names,
                                                         BLangStructureTypeNode recordTypeNode,
                                                         BStructureType recordType,
                                                         BStructureType origStructureType, Location pos,
                                                         SymbolEnv env, PackageID pkgID) {
        BTypeSymbol mutableStructureSymbol = recordType.tsymbol;
        LinkedHashMap<String, BField> fields = new LinkedHashMap<>();
        for (BField origField : origStructureType.fields.values()) {

            BType mutableFieldType = origField.type;

            Name origFieldName = origField.name;
            BVarSymbol mutableFieldSymbol;
            if (mutableFieldType.tag == TypeTags.INVOKABLE && mutableFieldType.tsymbol != null) {
                mutableFieldSymbol = new BInvokableSymbol(origField.symbol.tag, origField.symbol.flags,
                        origFieldName, pkgID, mutableFieldType,
                        mutableStructureSymbol, origField.symbol.pos, SOURCE);
                BInvokableTypeSymbol tsymbol = (BInvokableTypeSymbol) mutableFieldType.tsymbol;
                BInvokableSymbol invokableSymbol = (BInvokableSymbol) mutableFieldSymbol;
                invokableSymbol.params = tsymbol.params == null ? null : new ArrayList<>(tsymbol.params);
                invokableSymbol.restParam = tsymbol.restParam;
                invokableSymbol.retType = tsymbol.returnType;
                invokableSymbol.flags = tsymbol.flags;
            } else if (mutableFieldType == symTable.semanticError) {
                // Can only happen for records.
                mutableFieldSymbol = new BVarSymbol(origField.symbol.flags | Flags.OPTIONAL,
                        origFieldName, pkgID, symTable.neverType,
                        mutableStructureSymbol, origField.symbol.pos, VIRTUAL);
            } else {
                mutableFieldSymbol = new BVarSymbol(origField.symbol.flags, origFieldName, pkgID,
                        mutableFieldType, mutableStructureSymbol,
                        origField.symbol.pos, VIRTUAL);
            }
            String nameString = origFieldName.value;
            fields.put(nameString, new BField(origFieldName, null, mutableFieldSymbol));
            mutableStructureSymbol.scope.define(origFieldName, mutableFieldSymbol);
            ((BLangRecordTypeNode) recordTypeNode).fields.add(createSimpleVariable(origField));
        }
        recordType.fields = fields;
        ((BRecordType) recordType).restFieldType = new BNoType(TypeTags.NONE);

        if (origStructureType.tag == TypeTags.OBJECT) {
            return;
        }

        BLangUserDefinedType origTypeRef = new BLangUserDefinedType(
                ASTBuilderUtil.createIdentifier(pos,
                        TypeDefBuilderHelper.getPackageAlias(env, pos.lineRange().filePath(),
                                origStructureType.tsymbol.pkgID)),
                ASTBuilderUtil.createIdentifier(pos, origStructureType.tsymbol.name.value));
        origTypeRef.pos = pos;
        origTypeRef.setBType(origStructureType);

        ((BLangRecordTypeNode) recordTypeNode).sealed = true;
    }

    private BLangSimpleVariable createSimpleVariable(BField field) {
        BLangSimpleVariable manualField = new BLangSimpleVariable();
        BLangIdentifier name = new BLangIdentifier();
        BLangFiniteTypeNode finiteTypeNode = (BLangFiniteTypeNode) TreeBuilder.createFiniteTypeNode();
        name.setValue(field.name.value);
        name.pos = field.pos;
        manualField.setName(name);

        manualField.flagSet = new HashSet<>();
        manualField.flagSet.add(Flag.PUBLIC);
        manualField.flagSet.add(Flag.REQUIRED);
        manualField.flagSet.add(Flag.FIELD);
        manualField.setBType(field.type);
        manualField.pos = field.pos;
        manualField.setDeterminedType(field.type);
        manualField.symbol = field.symbol;

        if (field.type.tag == TypeTags.RECORD) {


        }
        finiteTypeNode.pos = field.pos;
        finiteTypeNode.setBType(field.type);
        finiteTypeNode.setDeterminedType(field.type);
        finiteTypeNode.valueSpace = new ArrayList<>();
        for (BLangExpression value : ((BFiniteType) field.type).getValueSpace()) {
            if (value.getKind() == NodeKind.NUMERIC_LITERAL) {
                ((BLangNumericLiteral) value).originalValue = ((BLangNumericLiteral) value).value.toString();
            }
            finiteTypeNode.valueSpace.add(value);
        }
        manualField.typeNode = finiteTypeNode;
        return manualField;
    }
}
