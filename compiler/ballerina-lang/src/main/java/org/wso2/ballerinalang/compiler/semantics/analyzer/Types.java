/*
 *  Copyright (c) 2017, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
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

import org.ballerinalang.model.Name;
import org.ballerinalang.model.TreeBuilder;
import org.ballerinalang.model.elements.Flag;
import org.ballerinalang.model.tree.NodeKind;
import org.ballerinalang.model.types.TypeKind;
import org.ballerinalang.util.BLangCompilerConstants;
import org.ballerinalang.util.diagnostic.DiagnosticCode;
import org.wso2.ballerinalang.compiler.semantics.model.SymbolTable;
import org.wso2.ballerinalang.compiler.semantics.model.symbols.BAttachedFunction;
import org.wso2.ballerinalang.compiler.semantics.model.symbols.BCastOperatorSymbol;
import org.wso2.ballerinalang.compiler.semantics.model.symbols.BInvokableSymbol;
import org.wso2.ballerinalang.compiler.semantics.model.symbols.BObjectTypeSymbol;
import org.wso2.ballerinalang.compiler.semantics.model.symbols.BStructureTypeSymbol;
import org.wso2.ballerinalang.compiler.semantics.model.symbols.BSymbol;
import org.wso2.ballerinalang.compiler.semantics.model.symbols.BTypeSymbol;
import org.wso2.ballerinalang.compiler.semantics.model.symbols.BVarSymbol;
import org.wso2.ballerinalang.compiler.semantics.model.symbols.SymTag;
import org.wso2.ballerinalang.compiler.semantics.model.symbols.Symbols;
import org.wso2.ballerinalang.compiler.semantics.model.types.BAnyType;
import org.wso2.ballerinalang.compiler.semantics.model.types.BAnydataType;
import org.wso2.ballerinalang.compiler.semantics.model.types.BArrayType;
import org.wso2.ballerinalang.compiler.semantics.model.types.BBuiltInRefType;
import org.wso2.ballerinalang.compiler.semantics.model.types.BErrorType;
import org.wso2.ballerinalang.compiler.semantics.model.types.BField;
import org.wso2.ballerinalang.compiler.semantics.model.types.BFiniteType;
import org.wso2.ballerinalang.compiler.semantics.model.types.BFutureType;
import org.wso2.ballerinalang.compiler.semantics.model.types.BInvokableType;
import org.wso2.ballerinalang.compiler.semantics.model.types.BJSONType;
import org.wso2.ballerinalang.compiler.semantics.model.types.BMapType;
import org.wso2.ballerinalang.compiler.semantics.model.types.BObjectType;
import org.wso2.ballerinalang.compiler.semantics.model.types.BRecordType;
import org.wso2.ballerinalang.compiler.semantics.model.types.BSemanticErrorType;
import org.wso2.ballerinalang.compiler.semantics.model.types.BServiceType;
import org.wso2.ballerinalang.compiler.semantics.model.types.BStreamType;
import org.wso2.ballerinalang.compiler.semantics.model.types.BStructureType;
import org.wso2.ballerinalang.compiler.semantics.model.types.BTableType;
import org.wso2.ballerinalang.compiler.semantics.model.types.BTupleType;
import org.wso2.ballerinalang.compiler.semantics.model.types.BType;
import org.wso2.ballerinalang.compiler.semantics.model.types.BTypeVisitor;
import org.wso2.ballerinalang.compiler.semantics.model.types.BTypedescType;
import org.wso2.ballerinalang.compiler.semantics.model.types.BUnionType;
import org.wso2.ballerinalang.compiler.semantics.model.types.BXMLType;
import org.wso2.ballerinalang.compiler.tree.BLangFunction;
import org.wso2.ballerinalang.compiler.tree.clauses.BLangFromClause;
import org.wso2.ballerinalang.compiler.tree.expressions.BLangExpression;
import org.wso2.ballerinalang.compiler.tree.expressions.BLangLiteral;
import org.wso2.ballerinalang.compiler.tree.expressions.BLangTypeConversionExpr;
import org.wso2.ballerinalang.compiler.tree.statements.BLangForeach;
import org.wso2.ballerinalang.compiler.util.BArrayState;
import org.wso2.ballerinalang.compiler.util.CompilerContext;
import org.wso2.ballerinalang.compiler.util.Names;
import org.wso2.ballerinalang.compiler.util.NumericLiteralSupport;
import org.wso2.ballerinalang.compiler.util.TypeTags;
import org.wso2.ballerinalang.compiler.util.diagnotic.BLangDiagnosticLog;
import org.wso2.ballerinalang.compiler.util.diagnotic.DiagnosticPos;
import org.wso2.ballerinalang.util.Flags;
import org.wso2.ballerinalang.util.Lists;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static org.wso2.ballerinalang.compiler.semantics.model.SymbolTable.BBYTE_MAX_VALUE;
import static org.wso2.ballerinalang.compiler.semantics.model.SymbolTable.BBYTE_MIN_VALUE;

/**
 * This class consists of utility methods which operate on types.
 * These utility methods allows you to check the compatibility of two types,
 * i.e. check whether two types are equal, check whether one type is assignable to another type etc.
 *
 * @since 0.94
 */
public class Types {

    private static final CompilerContext.Key<Types> TYPES_KEY =
            new CompilerContext.Key<>();

    private SymbolTable symTable;
    private SymbolResolver symResolver;
    private BLangDiagnosticLog dlog;
    private Names names;
    private int finiteTypeCount = 0;

    public static Types getInstance(CompilerContext context) {
        Types types = context.get(TYPES_KEY);
        if (types == null) {
            types = new Types(context);
        }

        return types;
    }

    public Types(CompilerContext context) {
        context.put(TYPES_KEY, this);

        this.symTable = SymbolTable.getInstance(context);
        this.symResolver = SymbolResolver.getInstance(context);
        this.dlog = BLangDiagnosticLog.getInstance(context);
        this.names = Names.getInstance(context);
    }

    BLangDiagnosticLog getDLog() {
        return this.dlog;
    }

    void setDLog(BLangDiagnosticLog dlog) {
        this.dlog = dlog;
    }

    public List<BType> checkTypes(BLangExpression node,
                                  List<BType> actualTypes,
                                  List<BType> expTypes) {
        List<BType> resTypes = new ArrayList<>();
        for (int i = 0; i < actualTypes.size(); i++) {
            resTypes.add(checkType(node, actualTypes.get(i), expTypes.size() > i ? expTypes.get(i) : symTable.noType));
        }
        return resTypes;
    }

    public BType checkType(BLangExpression node,
                           BType actualType,
                           BType expType) {
        return checkType(node, actualType, expType, DiagnosticCode.INCOMPATIBLE_TYPES);
    }

    public BType checkType(BLangExpression expr,
                           BType actualType,
                           BType expType,
                           DiagnosticCode diagCode) {
        expr.type = checkType(expr.pos, actualType, expType, diagCode);
        if (expr.type.tag == TypeTags.SEMANTIC_ERROR) {
            return expr.type;
        }

        // Set an implicit cast expression, if applicable
        setImplicitCastExpr(expr, actualType, expType);

        return expr.type;
    }

    public BType checkType(DiagnosticPos pos,
                           BType actualType,
                           BType expType,
                           DiagnosticCode diagCode) {
        if (expType.tag == TypeTags.SEMANTIC_ERROR) {
            return expType;
        } else if (expType.tag == TypeTags.NONE) {
            return actualType;
        } else if (actualType.tag == TypeTags.SEMANTIC_ERROR) {
            return actualType;
        } else if (isAssignable(actualType, expType)) {
            return actualType;
        }

        // e.g. incompatible types: expected 'int', found 'string'
        dlog.error(pos, diagCode, expType, actualType);
        return symTable.semanticError;
    }

    public boolean isJSONContext(BType type) {
        if (type.tag == TypeTags.UNION) {
            return ((BUnionType) type).getMemberTypes().stream().anyMatch(memType -> memType.tag == TypeTags.JSON);
        }
        return type.tag == TypeTags.JSON;
    }

    public boolean isLax(BType type) {
        switch (type.tag) {
            case TypeTags.JSON:
                return true;
            case TypeTags.MAP:
                return isLax(((BMapType) type).constraint);
            case TypeTags.UNION:
                return ((BUnionType) type).getMemberTypes().stream().allMatch(this::isLax);
        }
        return false;
    }

    public boolean isSameType(BType source, BType target) {
        return isSameType(source, target, new HashSet<>());
    }

    private boolean isSameType(BType source, BType target, Set<TypePair> unresolvedTypes) {
        // If we encounter two types that we are still resolving, then skip it.
        // This is done to avoid recursive checking of the same type.
        TypePair pair = new TypePair(source, target);
        if (unresolvedTypes.contains(pair)) {
            return true;
        }
        unresolvedTypes.add(pair);
        BTypeVisitor<BType, Boolean> sameTypeVisitor = new BSameTypeVisitor(unresolvedTypes);
        return target.accept(sameTypeVisitor, source);
    }

    public boolean isValueType(BType type) {
        switch (type.tag) {
            case TypeTags.BOOLEAN:
            case TypeTags.BYTE:
            case TypeTags.DECIMAL:
            case TypeTags.FLOAT:
            case TypeTags.INT:
            case TypeTags.STRING:
                return true;
            default:
                return false;
        }
    }

    boolean isBasicNumericType(BType type) {
        return type.tag < TypeTags.STRING;
    }

    boolean finiteTypeContainsNumericTypeValues(BFiniteType finiteType) {
        return finiteType.getValueSpace().stream().anyMatch(valueExpr -> isBasicNumericType(valueExpr.type));
    }

    private boolean containsNumericType(BType type) {
        if (type.tag == TypeTags.UNION) {
            return ((BUnionType) type).getMemberTypes().stream()
                    .anyMatch(this::containsNumericType);
        }

        return isBasicNumericType(type);
    }

    private boolean containsErrorType(BType type) {
        if (type.tag == TypeTags.UNION) {
            return ((BUnionType) type).getMemberTypes().stream()
                    .anyMatch(this::containsErrorType);
        }

        return type.tag == TypeTags.ERROR;
    }

    public boolean isLikeAnydataOrNotNil(BType type) {
        return type.tag != TypeTags.NIL && (type.isAnydata() || isLikeAnydata(type));
    }

    private boolean isLikeAnydata(BType type) {
        return isLikeAnydata(type, new HashSet<>());
    }

    private boolean isLikeAnydata(BType type, Set<BType> unresolvedTypes) {
        int typeTag = type.tag;
        if (typeTag == TypeTags.ANY) {
            return true;
        }

        // check for anydata element/member types as part of recursive calls with structured/union types
        if (type.tag == TypeTags.RECORD) {
            if (unresolvedTypes.contains(type)) {
                return true;
            } else {
                unresolvedTypes.add(type);
                if (type.isAnydata()) {
                    return true;
                }
            }
        } else if (type.isAnydata()) {
            return true;
        }

        if (type.tag == TypeTags.MAP && isLikeAnydata(((BMapType) type).constraint, unresolvedTypes)) {
            return true;
        }

        if (type.tag == TypeTags.RECORD) {
            BRecordType recordType = (BRecordType) type;
            return recordType.fields.stream()
                    .noneMatch(field -> !Symbols.isFlagOn(field.symbol.flags, Flags.OPTIONAL) &&
                            !(isLikeAnydata(field.type, unresolvedTypes)));
        }

        if (type.tag == TypeTags.UNION) {
            BUnionType unionType = (BUnionType) type;
            return unionType.getMemberTypes().stream().anyMatch(bType -> isLikeAnydata(bType, unresolvedTypes));
        }

        if (type.tag == TypeTags.TUPLE) {
            BTupleType tupleType = (BTupleType) type;
            return tupleType.getTupleTypes().stream().allMatch(bType -> isLikeAnydata(bType, unresolvedTypes));
        }

        return type.tag == TypeTags.ARRAY && isLikeAnydata(((BArrayType) type).eType, unresolvedTypes);
    }

    public boolean isSubTypeOfList(BType type) {
        if (type.tag != TypeTags.UNION) {
            return isSubTypeOfBaseType(type, TypeTags.ARRAY) || isSubTypeOfBaseType(type, TypeTags.TUPLE);
        }

        return ((BUnionType) type).getMemberTypes().stream().allMatch(this::isSubTypeOfList);
    }

    public boolean isSubTypeOfMapping(BType type) {
        if (type.tag != TypeTags.UNION) {
            return isSubTypeOfBaseType(type, TypeTags.MAP) || isSubTypeOfBaseType(type, TypeTags.RECORD);
        }

        return ((BUnionType) type).getMemberTypes().stream().allMatch(this::isSubTypeOfMapping);
    }

    public boolean isSubTypeOfBaseType(BType type, int baseTypeTag) {
        if (type.tag != TypeTags.UNION) {
            return type.tag == baseTypeTag;
        }

        return ((BUnionType) type).getMemberTypes().stream().allMatch(memType -> memType.tag == baseTypeTag);
    }

    /**
     * Checks whether source type is assignable to the target type.
     * <p>
     * Source type is assignable to the target type if,
     * 1) the target type is any and the source type is not a value type.
     * 2) there exists an implicit cast symbol from source to target.
     * 3) both types are JSON and the target constraint is no type.
     * 4) both types are array type and both array types are assignable.
     * 5) both types are MAP and the target constraint is any type or constraints are structurally equivalent.
     *
     * @param source type.
     * @param target type.
     * @return true if source type is assignable to the target type.
     */
    public boolean isAssignable(BType source, BType target) {
        return isAssignable(source, target, new HashSet<>());
    }

    boolean isStampingAllowed(BType source, BType target) {
        return (isAssignable(source, target) || isAssignable(target, source) ||
                checkTypeEquivalencyForStamping(source, target) || checkTypeEquivalencyForStamping(target, source));
    }

    private boolean checkTypeEquivalencyForStamping(BType source, BType target) {
        if (target.tag == TypeTags.RECORD) {
            if (source.tag == TypeTags.RECORD) {
                TypePair pair = new TypePair(source, target);
                Set<TypePair> unresolvedTypes = new HashSet<>();
                unresolvedTypes.add(pair);
                return checkRecordEquivalencyForStamping((BRecordType) source, (BRecordType) target, unresolvedTypes);
            } else if (source.tag == TypeTags.MAP) {
                int mapConstraintTypeTag = ((BMapType) source).constraint.tag;
                if ((!(mapConstraintTypeTag == TypeTags.ANY || mapConstraintTypeTag == TypeTags.ANYDATA)) &&
                        ((BRecordType) target).sealed) {
                    for (BField field : ((BStructureType) target).getFields()) {
                        if (field.getType().tag != mapConstraintTypeTag) {
                            return false;
                        }
                    }
                }
                return true;
            }
        } else if (target.tag == TypeTags.JSON) {
            return source.tag == TypeTags.JSON || source.tag == TypeTags.RECORD || source.tag == TypeTags.MAP;
        } else if (target.tag == TypeTags.MAP) {
            if (source.tag == TypeTags.MAP) {
                return isStampingAllowed(((BMapType) source).getConstraint(), ((BMapType) target).getConstraint());
            } else if (source.tag == TypeTags.UNION) {
                return checkUnionEquivalencyForStamping(source, target);
            }
        } else if (target.tag == TypeTags.ARRAY) {
            if (source.tag == TypeTags.JSON) {
                return true;
            } else if (source.tag == TypeTags.TUPLE) {
                BType arrayElementType = ((BArrayType) target).eType;
                for (BType tupleMemberType : ((BTupleType) source).getTupleTypes()) {
                    if (!isStampingAllowed(tupleMemberType, arrayElementType)) {
                        return false;
                    }
                }
                return true;
            } else if (source.tag == TypeTags.ARRAY) {
                return checkTypeEquivalencyForStamping(((BArrayType) source).eType, ((BArrayType) target).eType);
            }
        } else if (target.tag == TypeTags.UNION) {
            return checkUnionEquivalencyForStamping(source, target);
        } else if (target.tag == TypeTags.TUPLE && source.tag == TypeTags.TUPLE) {
            return checkTupleEquivalencyForStamping(source, target);
        }

        return false;
    }

    private boolean checkRecordEquivalencyForStamping(BRecordType rhsType, BRecordType lhsType,
                                                      Set<TypePair> unresolvedTypes) {
        // Both records should be public or private.
        // Get the XOR of both flags(masks)
        // If both are public, then public bit should be 0;
        // If both are private, then public bit should be 0;
        // The public bit is on means, one is public, and the other one is private.
        if (Symbols.isFlagOn(lhsType.tsymbol.flags ^ rhsType.tsymbol.flags, Flags.PUBLIC)) {
            return false;
        }

        // If both records are private, they should be in the same package.
        if (Symbols.isPrivate(lhsType.tsymbol) && rhsType.tsymbol.pkgID != lhsType.tsymbol.pkgID) {
            return false;
        }

        // RHS type should have at least all the fields as well attached functions of LHS type.
        if (lhsType.fields.size() > rhsType.fields.size()) {
            return false;
        }

        // If only one is a closed record, the records aren't equivalent
        if (lhsType.sealed && !rhsType.sealed) {
            return false;
        }

        return checkFieldEquivalencyForStamping(lhsType, rhsType, unresolvedTypes);
    }

    private boolean checkFieldEquivalencyForStamping(BStructureType lhsType, BStructureType rhsType,
                                                     Set<TypePair> unresolvedTypes) {
        Map<Name, BField> rhsFields = rhsType.fields.stream().collect(
                Collectors.toMap(BField::getName, field -> field));

        for (BField lhsField : lhsType.fields) {
            BField rhsField = rhsFields.get(lhsField.name);

            if (rhsField == null || !isStampingAllowed(rhsField.type, lhsField.type)) {
                return false;
            }
        }

        Map<Name, BField> lhsFields = lhsType.fields.stream().collect(
                Collectors.toMap(BField::getName, field -> field));
        for (BField rhsField : rhsType.fields) {
            BField lhsField = lhsFields.get(rhsField.name);

            if (lhsField == null && !isStampingAllowed(rhsField.type, ((BRecordType) lhsType).restFieldType)) {
                return false;
            }
        }

        return true;
    }

    private boolean checkUnionEquivalencyForStamping(BType source, BType target) {
        Set<BType> sourceTypes = new LinkedHashSet<>();
        Set<BType> targetTypes = new LinkedHashSet<>();

        if (source.tag == TypeTags.UNION) {
            BUnionType sourceUnionType = (BUnionType) source;
            sourceTypes.addAll(sourceUnionType.getMemberTypes());
        } else {
            sourceTypes.add(source);
        }

        if (target.tag == TypeTags.UNION) {
            BUnionType targetUnionType = (BUnionType) target;
            targetTypes.addAll(targetUnionType.getMemberTypes());
        } else {
            targetTypes.add(target);
        }

        boolean notAssignable = sourceTypes
                .stream()
                .map(s -> targetTypes
                        .stream()
                        .anyMatch(t -> isStampingAllowed(s, t)))
                .anyMatch(assignable -> !assignable);

        return !notAssignable;
    }

    private boolean checkTupleEquivalencyForStamping(BType source, BType target) {
        if (source.tag != TypeTags.TUPLE || target.tag != TypeTags.TUPLE) {
            return false;
        }

        BTupleType lhsTupleType = (BTupleType) target;
        BTupleType rhsTupleType = (BTupleType) source;

        if (lhsTupleType.tupleTypes.size() != rhsTupleType.tupleTypes.size()) {
            return false;
        }

        for (int i = 0; i < lhsTupleType.tupleTypes.size(); i++) {
            if (!isStampingAllowed(rhsTupleType.tupleTypes.get(i), lhsTupleType.tupleTypes.get(i))) {
                return false;
            }
        }
        return true;
    }

    private boolean isAssignable(BType source, BType target, Set<TypePair> unresolvedTypes) {

        if (isSameType(source, target)) {
            return true;
        }

        int sourceTag = source.tag;
        int targetTag = target.tag;

        if (sourceTag == TypeTags.BYTE && targetTag == TypeTags.INT) {
            return true;
        }

        if (sourceTag == TypeTags.ERROR && targetTag == TypeTags.ERROR) {
            return isErrorTypeAssignable((BErrorType) source, (BErrorType) target, unresolvedTypes);
        } else if (sourceTag == TypeTags.ERROR && targetTag == TypeTags.ANY) {
            return false;
        }

        if (sourceTag == TypeTags.NIL && (isNullable(target) || targetTag == TypeTags.JSON)) {
            return true;
        }

        // TODO: Remove the isValueType() check
        if (targetTag == TypeTags.ANY && !containsErrorType(source) && !isValueType(source)) {
            return true;
        }

        if (targetTag == TypeTags.ANYDATA && !containsErrorType(source) && source.isAnydata()) {
            return true;
        }

        if (targetTag == TypeTags.MAP && sourceTag == TypeTags.RECORD) {
            BRecordType recordType = (BRecordType) source;
            return isAssignableRecordType(recordType, (BMapType) target);
        }

        if (target.getKind() == TypeKind.SERVICE && source.getKind() == TypeKind.SERVICE) {
            // Special casing services, until we figure out service type concept.
            return true;
        }

        if (targetTag == TypeTags.TYPEDESC && sourceTag == TypeTags.TYPEDESC) {
            return isAssignable(((BTypedescType) source).constraint, (((BTypedescType) target).constraint),
                    unresolvedTypes);
        }

        if (targetTag == TypeTags.TABLE && sourceTag == TypeTags.TABLE) {
            return isAssignable(((BTableType) source).constraint, (((BTableType) target).constraint),
                                unresolvedTypes);
        }

        if (targetTag == TypeTags.STREAM && sourceTag == TypeTags.STREAM) {
            return isAssignable(((BStreamType) source).constraint, ((BStreamType) target).constraint, unresolvedTypes);
        }

        BSymbol symbol = symResolver.resolveImplicitCastOp(source, target);
        if (symbol != symTable.notFoundSymbol) {
            return true;
        }

        if (sourceTag == TypeTags.FINITE) {
            return isFiniteTypeAssignable((BFiniteType) source, target, unresolvedTypes);
        }

        if ((targetTag == TypeTags.UNION || sourceTag == TypeTags.UNION) &&
                isAssignableToUnionType(source, target, unresolvedTypes)) {
            return true;
        }

        if (targetTag == TypeTags.JSON) {
            if (sourceTag == TypeTags.JSON) {
                return true;
            }

            if (sourceTag == TypeTags.ARRAY) {
                return isArrayTypesAssignable(source, target, unresolvedTypes);
            }

            if (sourceTag == TypeTags.MAP) {
                return isAssignable(((BMapType) source).constraint, target, unresolvedTypes);
            }
        }

        if (targetTag == TypeTags.FUTURE && sourceTag == TypeTags.FUTURE) {
            if (((BFutureType) target).constraint.tag == TypeTags.NONE) {
                return true;
            }
            return isAssignable(((BFutureType) source).constraint, ((BFutureType) target).constraint, unresolvedTypes);
        }

        if (targetTag == TypeTags.MAP && sourceTag == TypeTags.MAP) {
            // Here source condition is added for prevent assigning map union constrained
            // to map any constrained.
            if (((BMapType) target).constraint.tag == TypeTags.ANY &&
                    ((BMapType) source).constraint.tag != TypeTags.UNION) {
                return true;
            }

            return isAssignable(((BMapType) source).constraint, ((BMapType) target).constraint, unresolvedTypes);
        }

        if (targetTag == TypeTags.MAP && sourceTag == TypeTags.RECORD) {
            BType mapConstraint = ((BMapType) target).constraint;
            BRecordType srcRec = (BRecordType) source;
            boolean hasIncompatibleType = srcRec.fields
                    .stream().anyMatch(field -> !isAssignable(field.type, mapConstraint));
            return !hasIncompatibleType && isAssignable(srcRec.restFieldType, mapConstraint);
        }

        if ((sourceTag == TypeTags.OBJECT || sourceTag == TypeTags.RECORD)
                && (targetTag == TypeTags.OBJECT || targetTag == TypeTags.RECORD)) {
            return checkStructEquivalency(source, target, unresolvedTypes);
        }

        if (sourceTag == TypeTags.TUPLE && targetTag == TypeTags.ARRAY) {
            return isTupleTypeAssignableToArrayType((BTupleType) source, (BArrayType) target, unresolvedTypes);
        }

        if (sourceTag == TypeTags.ARRAY && targetTag == TypeTags.TUPLE) {
            return isArrayTypeAssignableToTupleType((BArrayType) source, (BTupleType) target, unresolvedTypes);
        }

        if (sourceTag == TypeTags.TUPLE || targetTag == TypeTags.TUPLE) {
            return isTupleTypeAssignable(source, target, unresolvedTypes);
        }

        if (sourceTag == TypeTags.INVOKABLE && targetTag == TypeTags.INVOKABLE) {
            return isFunctionTypeAssignable((BInvokableType) source, (BInvokableType) target, new HashSet<>());
        }

        return sourceTag == TypeTags.ARRAY && targetTag == TypeTags.ARRAY &&
                isArrayTypesAssignable(source, target, unresolvedTypes);
    }

    private boolean isAssignableRecordType(BRecordType recordType, BMapType targetMapType) {
        if (recordType.sealed) {
            return recordFieldsAssignableToMap(recordType, targetMapType);
        } else {
            return isAssignable(recordType.restFieldType, targetMapType.constraint)
                    && recordFieldsAssignableToMap(recordType, targetMapType);
        }
    }

    private boolean recordFieldsAssignableToMap(BRecordType recordType, BMapType targetMapType) {
        return recordType.fields.stream().allMatch(field -> isAssignable(field.type, targetMapType.constraint));
    }

    private boolean isErrorTypeAssignable(BErrorType source, BErrorType target, Set<TypePair> unresolvedTypes) {
        if (target == symTable.errorType) {
            return true;
        }
        TypePair pair = new TypePair(source, target);
        if (unresolvedTypes.contains(pair)) {
            return true;
        }
        unresolvedTypes.add(pair);
        return isAssignable(source.reasonType, target.reasonType, unresolvedTypes) && 
                isAssignable(source.detailType, target.detailType, unresolvedTypes);
    }

    private boolean isTupleTypeAssignable(BType source, BType target, Set<TypePair> unresolvedTypes) {
        if (source.tag != TypeTags.TUPLE || target.tag != TypeTags.TUPLE) {
            return false;
        }

        BTupleType lhsTupleType = (BTupleType) target;
        BTupleType rhsTupleType = (BTupleType) source;

        if (lhsTupleType.restType == null && rhsTupleType.restType != null) {
            return false;
        }

        if (lhsTupleType.restType == null && lhsTupleType.tupleTypes.size() != rhsTupleType.tupleTypes.size()) {
            return false;
        }

        if (lhsTupleType.restType != null && rhsTupleType.restType != null) {
            if (!isAssignable(rhsTupleType.restType, lhsTupleType.restType, unresolvedTypes)) {
                return false;
            }
        }

        for (int i = 0; i < rhsTupleType.tupleTypes.size(); i++) {
            BType lhsType = (lhsTupleType.tupleTypes.size() > i)
                    ? lhsTupleType.tupleTypes.get(i) : lhsTupleType.restType;
            if (!isAssignable(rhsTupleType.tupleTypes.get(i), lhsType, unresolvedTypes)) {
                return false;
            }
        }
        return true;
    }

    private boolean isTupleTypeAssignableToArrayType(BTupleType source, BArrayType target,
                                                     Set<TypePair> unresolvedTypes) {
        if (target.state != BArrayState.UNSEALED
                && (source.restType != null || source.tupleTypes.size() != target.size)) {
            return false;
        }

        List<BType> sourceTypes = new ArrayList<>(source.tupleTypes);
        if (source.restType != null) {
            sourceTypes.add(source.restType);
        }
        return sourceTypes.stream()
                .allMatch(tupleElemType -> isAssignable(tupleElemType, target.eType, unresolvedTypes));
    }

    private boolean isArrayTypeAssignableToTupleType(BArrayType source, BTupleType target,
                                                     Set<TypePair> unresolvedTypes) {
        if (!target.tupleTypes.isEmpty()) {
            if (source.state == BArrayState.UNSEALED) {
                // [int, int, int...] = int[] || [int, int] = int[]
                return false;
            }

            if (target.restType != null && target.tupleTypes.size() > source.size) {
                // [int, int, int...] = int[1]
                return false;
            }

            if (target.restType == null && target.tupleTypes.size() != source.size) {
                // [int, int] = int[1], [int, int] = int[3]
                return false;
            }

        }

        List<BType> targetTypes = new ArrayList<>(target.tupleTypes);
        if (target.restType != null) {
            targetTypes.add(target.restType);
        }
        return targetTypes.stream()
                .allMatch(tupleElemType -> isAssignable(source.eType, tupleElemType, unresolvedTypes));
    }

    public boolean isArrayTypesAssignable(BType source, BType target, Set<TypePair> unresolvedTypes) {
        if (target.tag == TypeTags.ARRAY && source.tag == TypeTags.ARRAY) {
            // Both types are array types
            BArrayType lhsArrayType = (BArrayType) target;
            BArrayType rhsArrayType = (BArrayType) source;
            if (lhsArrayType.state == BArrayState.UNSEALED) {
                return isArrayTypesAssignable(rhsArrayType.eType, lhsArrayType.eType, unresolvedTypes);
            }
            return checkSealedArraySizeEquality(rhsArrayType, lhsArrayType)
                    && isArrayTypesAssignable(rhsArrayType.eType, lhsArrayType.eType, unresolvedTypes);

        } else if (source.tag == TypeTags.ARRAY) {
            // Only the right-hand side is an array type

            // If the target type is a JSON, then element type of the rhs array
            // should only be a JSON supported type.
            if (target.tag == TypeTags.JSON) {
                return isAssignable(((BArrayType) source).getElementType(), target, unresolvedTypes);
            }

            if (target.tag == TypeTags.UNION) {
                return isAssignable(source, target);
            }

            // Then lhs type should 'any' type
            return target.tag == TypeTags.ANY;

        } else if (target.tag == TypeTags.ARRAY) {
            // Only the left-hand side is an array type
            return false;
        }

        // Now both types are not array types and they have to be assignable
        if (isAssignable(source, target, unresolvedTypes)) {
            return true;
        }

        if (target.tag == TypeTags.UNION) {
            return isAssignable(source, target, unresolvedTypes);
        }

        // In this case, lhs type should be of type 'any' and the rhs type cannot be a value type
        return target.tag == TypeTags.ANY && !isValueType(source);
    }

    private boolean isFunctionTypeAssignable(BInvokableType source, BInvokableType target,
                                             Set<TypePair> unresolvedTypes) {
        // For invokable types with typeParam parameters, we have to check whether the source param types are
        // covariant with the target param types.
        if (containsTypeParams(target)) {
            // TODO: 7/4/19 See if the below code can be generalized to avoid code duplication
            if (source.paramTypes.size() != target.paramTypes.size()) {
                return false;
            }

            for (int i = 0; i < source.paramTypes.size(); i++) {
                BType sourceParam = source.paramTypes.get(i);
                BType targetParam = target.paramTypes.get(i);
                boolean isTypeParam = TypeParamAnalyzer.isTypeParam(targetParam);

                if (isTypeParam) {
                    if (!isAssignable(sourceParam, targetParam)) {
                        return false;
                    }
                } else {
                    if (!isAssignable(targetParam, sourceParam)) {
                        return false;
                    }
                }
            }

            if (source.retType == null && target.retType == null) {
                return true;
            } else if (source.retType == null || target.retType == null) {
                return false;
            }

            // Source return type should be covariant with target return type
            return isAssignable(source.retType, target.retType, unresolvedTypes);
        }

        // Source param types should be contravariant with target param types. Hence s and t switched when checking
        // assignability.
        return checkFunctionTypeEquality(source, target, unresolvedTypes, (s, t, ut) -> isAssignable(t, s, ut));
    }

    private boolean containsTypeParams(BInvokableType type) {
        boolean hasParameterizedTypes = type.paramTypes.stream()
                .anyMatch(t -> {
                    if (t.tag == TypeTags.FUNCTION_POINTER) {
                        return containsTypeParams((BInvokableType) t);
                    }
                    return TypeParamAnalyzer.isTypeParam(t);
                });

        if (hasParameterizedTypes) {
            return hasParameterizedTypes;
        }

        if (type.retType.tag == TypeTags.FUNCTION_POINTER) {
            return containsTypeParams((BInvokableType) type.retType);
        }

        return TypeParamAnalyzer.isTypeParam(type.retType);
    }

    private boolean isSameFunctionType(BInvokableType source, BInvokableType target, Set<TypePair> unresolvedTypes) {
        return checkFunctionTypeEquality(source, target, unresolvedTypes, this::isSameType);
    }

    private boolean checkFunctionTypeEquality(BInvokableType source, BInvokableType target,
                                              Set<TypePair> unresolvedTypes, TypeEqualityPredicate equality) {
        if (source.paramTypes.size() != target.paramTypes.size()) {
            return false;
        }

        for (int i = 0; i < source.paramTypes.size(); i++) {
            if (!equality.test(source.paramTypes.get(i), target.paramTypes.get(i), unresolvedTypes)) {
                return false;
            }
        }

        if ((source.restType != null && target.restType == null) ||
                target.restType != null && source.restType == null) {
            return false;
        } else if (source.restType != null && !equality.test(source.restType, target.restType, unresolvedTypes)) {
            return false;
        }

        if (source.retType == null && target.retType == null) {
            return true;
        } else if (source.retType == null || target.retType == null) {
            return false;
        }

        // Source return type should be covariant with target return type
        return isAssignable(source.retType, target.retType, unresolvedTypes);
    }

    public boolean checkArrayEquality(BType source, BType target, Set<TypePair> unresolvedTypes) {
        if (target.tag != TypeTags.ARRAY || source.tag != TypeTags.ARRAY) {
            return false;
        }

        BArrayType lhsArrayType = (BArrayType) target;
        BArrayType rhsArrayType = (BArrayType) source;
        if (lhsArrayType.state == BArrayState.UNSEALED) {
            return rhsArrayType.state == BArrayState.UNSEALED &&
                    isSameType(lhsArrayType.eType, rhsArrayType.eType, unresolvedTypes);
        }

        return checkSealedArraySizeEquality(rhsArrayType, lhsArrayType)
                && isSameType(lhsArrayType.eType, rhsArrayType.eType, unresolvedTypes);
    }

    public boolean checkSealedArraySizeEquality(BArrayType rhsArrayType, BArrayType lhsArrayType) {
        return lhsArrayType.size == rhsArrayType.size;
    }

    public boolean checkStructEquivalency(BType rhsType, BType lhsType) {
        return checkStructEquivalency(rhsType, lhsType, new HashSet<>());
    }

    private boolean checkStructEquivalency(BType rhsType, BType lhsType, Set<TypePair> unresolvedTypes) {
        // If we encounter two types that we are still resolving, then skip it.
        // This is done to avoid recursive checking of the same type.
        TypePair pair = new TypePair(rhsType, lhsType);
        if (unresolvedTypes.contains(pair)) {
            return true;
        }
        unresolvedTypes.add(pair);

        if (rhsType.tag == TypeTags.OBJECT && lhsType.tag == TypeTags.OBJECT) {
            return checkObjectEquivalency((BObjectType) rhsType, (BObjectType) lhsType, unresolvedTypes);
        }

        if (rhsType.tag == TypeTags.RECORD && lhsType.tag == TypeTags.RECORD) {
            return checkRecordEquivalency((BRecordType) rhsType, (BRecordType) lhsType, unresolvedTypes);
        }

        return false;
    }

    public boolean checkObjectEquivalency(BObjectType rhsType, BObjectType lhsType, Set<TypePair> unresolvedTypes) {
        BObjectTypeSymbol lhsStructSymbol = (BObjectTypeSymbol) lhsType.tsymbol;
        BObjectTypeSymbol rhsStructSymbol = (BObjectTypeSymbol) rhsType.tsymbol;
        List<BAttachedFunction> lhsFuncs = lhsStructSymbol.attachedFuncs;
        List<BAttachedFunction> rhsFuncs = ((BObjectTypeSymbol) rhsType.tsymbol).attachedFuncs;
        int lhsAttachedFuncCount = getObjectFuncCount(lhsStructSymbol);
        int rhsAttachedFuncCount = getObjectFuncCount(rhsStructSymbol);

        // RHS type should have at least all the fields as well attached functions of LHS type.
        if (lhsType.fields.size() > rhsType.fields.size() || lhsAttachedFuncCount > rhsAttachedFuncCount) {
            return false;
        }

        // The LHS type cannot have any private members. 
        if (lhsType.getFields().stream().anyMatch(field -> Symbols.isPrivate(field.symbol)) ||
                lhsFuncs.stream().anyMatch(func -> Symbols.isPrivate(func.symbol))) {
            return false;
        }

        Map<Name, BField> rhsFields =
                rhsType.fields.stream().collect(Collectors.toMap(BField::getName, field -> field));

        for (BField lhsField : lhsType.fields) {
            BField rhsField = rhsFields.get(lhsField.name);
            if (rhsField == null || !isInSameVisibilityRegion(lhsField.symbol, rhsField.symbol)
                    || !isAssignable(rhsField.type, lhsField.type)) {
                return false;
            }
        }

        for (BAttachedFunction lhsFunc : lhsFuncs) {
            if (lhsFunc == lhsStructSymbol.initializerFunc) {
                continue;
            }

            BAttachedFunction rhsFunc = getMatchingInvokableType(rhsFuncs, lhsFunc, unresolvedTypes);
            if (rhsFunc == null || !isInSameVisibilityRegion(lhsFunc.symbol, rhsFunc.symbol)) {
                return false;
            }
        }

        return true;
    }

    private int getObjectFuncCount(BObjectTypeSymbol sym) {
        // If an explicit initializer is available, it could mean,
        // 1) User explicitly defined an initializer
        // 2) The object type is coming from an already compiled source, hence the initializer is already set.
        //    If it's coming from a compiled binary, the attached functions list of the symbol would already contain
        //    the initializer in it.
        if (sym.initializerFunc != null && sym.attachedFuncs.contains(sym.initializerFunc)) {
            return sym.attachedFuncs.size() - 1;
        }
        return sym.attachedFuncs.size();
    }

    public boolean checkRecordEquivalency(BRecordType rhsType, BRecordType lhsType, Set<TypePair> unresolvedTypes) {
        // If the LHS record is closed and the RHS record is open, the records aren't equivalent
        if (lhsType.sealed && !rhsType.sealed) {
            return false;
        }

        // If both are open records, the rest field type of the RHS record should be assignable to the rest field
        // type of the LHS type.
        if (!rhsType.sealed && !isAssignable(rhsType.restFieldType, lhsType.restFieldType, unresolvedTypes)) {
            return false;
        }

        return checkFieldEquivalency(lhsType, rhsType, unresolvedTypes);
    }

    public void setForeachTypedBindingPatternType(BLangForeach foreachNode) {
        BType collectionType = foreachNode.collection.type;
        BType varType;
        switch (collectionType.tag) {
            case TypeTags.STRING:
                varType = symTable.stringType;
                break;
            case TypeTags.ARRAY:
                BArrayType arrayType = (BArrayType) collectionType;
                varType = arrayType.eType;
                break;
            case TypeTags.TUPLE:
                BTupleType tupleType = (BTupleType) collectionType;
                LinkedHashSet<BType> tupleTypes = new LinkedHashSet<>(tupleType.tupleTypes);
                if (tupleType.restType != null) {
                    tupleTypes.add(tupleType.restType);
                }
                varType = tupleTypes.size() == 1 ?
                        tupleTypes.iterator().next() : BUnionType.create(null, tupleTypes);
                break;
            case TypeTags.MAP:
                BMapType bMapType = (BMapType) collectionType;
                varType = bMapType.constraint;

                break;
            case TypeTags.RECORD:
                BRecordType recordType = (BRecordType) collectionType;
                varType = inferRecordFieldType(recordType);
                break;
            case TypeTags.XML:
                varType = BUnionType.create(null, symTable.xmlType, symTable.stringType);
                break;
            case TypeTags.TABLE:
                BTableType tableType = (BTableType) collectionType;
                if (tableType.constraint.tag == TypeTags.NONE) {
                    varType = symTable.anydataType;
                    break;
                }
                varType = tableType.constraint;
                break;
            case TypeTags.STREAM:
                BStreamType streamType = (BStreamType) collectionType;
                if (streamType.constraint.tag == TypeTags.NONE) {
                    varType = symTable.anydataType;
                    break;
                }
                varType = streamType.constraint;
                break;
            case TypeTags.OBJECT:
                // check for iterable objects
                BUnionType nextMethodReturnType = getVarTypeFromIterableObject((BObjectType) collectionType);
                if (nextMethodReturnType != null) {
                    foreachNode.resultType = getRecordType(nextMethodReturnType);
                    foreachNode.nillableResultType = nextMethodReturnType;
                    foreachNode.varType = ((BRecordType) foreachNode.resultType).fields.get(0).type;
                    return;
                }
                dlog.error(foreachNode.collection.pos, DiagnosticCode.INCOMPATIBLE_ITERATOR_FUNCTION_SIGNATURE);
                // fallthrough
            case TypeTags.SEMANTIC_ERROR:
                foreachNode.varType = symTable.semanticError;
                foreachNode.resultType = symTable.semanticError;
                foreachNode.nillableResultType = symTable.semanticError;
                return;
            default:
                foreachNode.varType = symTable.semanticError;
                foreachNode.resultType = symTable.semanticError;
                foreachNode.nillableResultType = symTable.semanticError;
                dlog.error(foreachNode.collection.pos, DiagnosticCode.ITERABLE_NOT_SUPPORTED_COLLECTION,
                        collectionType);
                return;
        }

        BInvokableSymbol iteratorSymbol = (BInvokableSymbol) symResolver.lookupLangLibMethod(collectionType,
                names.fromString(BLangCompilerConstants.ITERABLE_COLLECTION_ITERATOR_FUNC));
        BUnionType nextMethodReturnType =
                (BUnionType) getResultTypeOfNextInvocation((BObjectType) iteratorSymbol.retType);
        foreachNode.varType = varType;
        foreachNode.resultType = getRecordType(nextMethodReturnType);
        foreachNode.nillableResultType = nextMethodReturnType;
    }

    public void setFromClauseTypedBindingPatternType(BLangFromClause fromClause) {
        if (fromClause.collection == null) {
            //not-possible
            return;
        }

        BType collectionType = fromClause.collection.type;
        BType varType;
        switch (collectionType.tag) {
            case TypeTags.STRING:
                varType = symTable.stringType;
                break;
            case TypeTags.ARRAY:
                BArrayType arrayType = (BArrayType) collectionType;
                varType = arrayType.eType;
                break;
            case TypeTags.TUPLE:
                BTupleType tupleType = (BTupleType) collectionType;
                LinkedHashSet<BType> tupleTypes = new LinkedHashSet<>(tupleType.tupleTypes);
                if (tupleType.restType != null) {
                    tupleTypes.add(tupleType.restType);
                }
                varType = tupleTypes.size() == 1 ?
                        tupleTypes.iterator().next() : BUnionType.create(null, tupleTypes);
                break;
            case TypeTags.MAP:
                BMapType bMapType = (BMapType) collectionType;
                varType = bMapType.constraint;

                break;
            case TypeTags.RECORD:
                BRecordType recordType = (BRecordType) collectionType;
                varType = inferRecordFieldType(recordType);
                break;
            case TypeTags.XML:
                varType = BUnionType.create(null, symTable.xmlType, symTable.stringType);
                break;
            case TypeTags.TABLE:
                BTableType tableType = (BTableType) collectionType;
                if (tableType.constraint.tag == TypeTags.NONE) {
                    varType = symTable.anydataType;
                    break;
                }
                varType = tableType.constraint;
                break;
            case TypeTags.STREAM:
                BStreamType streamType = (BStreamType) collectionType;
                if (streamType.constraint.tag == TypeTags.NONE) {
                    varType = symTable.anydataType;
                    break;
                }
                varType = streamType.constraint;
                break;
            case TypeTags.OBJECT:
                // check for iterable objects
                BUnionType nextMethodReturnType = getVarTypeFromIterableObject((BObjectType) collectionType);
                if (nextMethodReturnType != null) {
                    fromClause.resultType = getRecordType(nextMethodReturnType);
                    fromClause.nillableResultType = nextMethodReturnType;
                    fromClause.varType = ((BRecordType) fromClause.resultType).fields.get(0).type;
                    return;
                }
                dlog.error(fromClause.collection.pos, DiagnosticCode.INCOMPATIBLE_ITERATOR_FUNCTION_SIGNATURE);
                // fallthrough
            case TypeTags.SEMANTIC_ERROR:
                fromClause.varType = symTable.semanticError;
                fromClause.resultType = symTable.semanticError;
                fromClause.nillableResultType = symTable.semanticError;
                return;
            default:
                fromClause.varType = symTable.semanticError;
                fromClause.resultType = symTable.semanticError;
                fromClause.nillableResultType = symTable.semanticError;
                dlog.error(fromClause.collection.pos, DiagnosticCode.ITERABLE_NOT_SUPPORTED_COLLECTION,
                        collectionType);
                return;
        }

        BInvokableSymbol iteratorSymbol = (BInvokableSymbol) symResolver.lookupLangLibMethod(collectionType,
                names.fromString(BLangCompilerConstants.ITERABLE_COLLECTION_ITERATOR_FUNC));
        BUnionType nextMethodReturnType =
                (BUnionType) getResultTypeOfNextInvocation((BObjectType) iteratorSymbol.retType);
        fromClause.varType = varType;
        fromClause.resultType = getRecordType(nextMethodReturnType);
        fromClause.nillableResultType = nextMethodReturnType;
    }

    private BUnionType getVarTypeFromIterableObject(BObjectType collectionType) {
        BObjectTypeSymbol objectTypeSymbol = (BObjectTypeSymbol) collectionType.tsymbol;
        for (BAttachedFunction func : objectTypeSymbol.attachedFuncs) {
            if (func.funcName.value.equals(BLangCompilerConstants.ITERABLE_OBJECT_ITERATOR_FUNC)) {
                return getVarTypeFromIteratorFunc(func);
            }
        }

        return null;
    }

    private BUnionType getVarTypeFromIteratorFunc(BAttachedFunction candidateIteratorFunc) {
        if (!candidateIteratorFunc.type.paramTypes.isEmpty()) {
            return null;
        }

        BType returnType = candidateIteratorFunc.type.retType;
        // abstract object {public function next() returns record {|int value;|}?;}
        return getVarTypeFromIteratorFuncReturnType(returnType);
    }

    private BUnionType getVarTypeFromIteratorFuncReturnType(BType returnType) {
        BObjectTypeSymbol objectTypeSymbol;
        if (returnType.tag != TypeTags.OBJECT) {
            return null;
        }

        objectTypeSymbol = (BObjectTypeSymbol) returnType.tsymbol;
        for (BAttachedFunction func : objectTypeSymbol.attachedFuncs) {
            if (func.funcName.value.equals(BLangCompilerConstants.NEXT_FUNC)) {
                return getVarTypeFromNextFunc(func);
            }
        }

        return null;
    }

    private BUnionType getVarTypeFromNextFunc(BAttachedFunction nextFunc) {
        BType returnType;
        if (!nextFunc.type.paramTypes.isEmpty()) {
            return null;
        }

        returnType = nextFunc.type.retType;
        // Check if the next function return type has the union type,
        // record {|int value;|}|();
        if (checkNextFuncReturnType(returnType)) {
            return (BUnionType) returnType;
        }

        return null;
    }

    private boolean checkNextFuncReturnType(BType returnType) {
        if (returnType.tag != TypeTags.UNION) {
            return false;
        }

        List<BType> types = new ArrayList<>(((BUnionType) returnType).getMemberTypes());

        if (!types.removeIf(type -> type.tag == TypeTags.NIL)) {
            return false;
        }

        if (types.size() != 1) {
            return false;
        }

        if (types.get(0).tag != TypeTags.RECORD) {
            return false;
        }

        BRecordType recordType = (BRecordType) types.get(0);
        // Check if the union type has the record type,
        // record {|int value;|};
        return checkRecordTypeInNextFuncReturnType(recordType);
    }

    private boolean checkRecordTypeInNextFuncReturnType(BRecordType recordType) {
        if (!recordType.sealed) {
            return false;
        }

        if (recordType.fields.size() != 1) {
            return false;
        }

        for (BField field : recordType.fields) {
            if (field.name.value.equals(BLangCompilerConstants.VALUE_FIELD)) {
                return true;
            }
        }

        return false;
    }

    private BRecordType getRecordType(BUnionType type) {
        for (BType member : type.getMemberTypes()) {
            if (member.tag == TypeTags.RECORD) {
                return (BRecordType) member;
            }
        }
        return null;
    }

    private BType getResultTypeOfNextInvocation(BObjectType iteratorType) {
        BAttachedFunction nextFunc = getNextFunc(iteratorType);
        return Objects.requireNonNull(nextFunc).type.retType;
    }

    private BAttachedFunction getNextFunc(BObjectType iteratorType) {
        BObjectTypeSymbol iteratorSymbol = (BObjectTypeSymbol) iteratorType.tsymbol;
        for (BAttachedFunction bAttachedFunction : iteratorSymbol.attachedFuncs) {
            if (bAttachedFunction.funcName.value
                    .equals(BLangCompilerConstants.NEXT_FUNC)) {
                return bAttachedFunction;
            }
        }
        return null;
    }

    public BType inferRecordFieldType(BRecordType recordType) {
        List<BField> fields = recordType.fields;
        BUnionType unionType = BUnionType.create(null);

        if (!recordType.sealed) {
            unionType.add(recordType.restFieldType);
        }

        for (BField field : fields) {
            if (isAssignable(field.type, unionType)) {
                continue;
            }

            if (isAssignable(unionType, field.type)) {
                unionType = BUnionType.create(null);
            }

            unionType.add(field.type);
        }

        if (unionType.getMemberTypes().size() > 1) {
            unionType.tsymbol = Symbols.createTypeSymbol(SymTag.UNION_TYPE, Flags.asMask(EnumSet.of(Flag.PUBLIC)),
                    Names.EMPTY, recordType.tsymbol.pkgID, null, recordType.tsymbol.owner);
            return unionType;
        }

        return unionType.getMemberTypes().iterator().next();
    }

    public BSymbol getImplicitCastOpSymbol(BType actualType, BType expType) {
        BSymbol symbol = symResolver.resolveImplicitCastOp(actualType, expType);
        if ((expType.tag == TypeTags.UNION || expType.tag == TypeTags.FINITE) && isValueType(actualType)) {
            symbol = symResolver.resolveImplicitCastOp(actualType, symTable.anyType);
        }

        if (symbol != symTable.notFoundSymbol) {
            return symbol;
        }

        if (actualType.tag == TypeTags.BYTE && expType.tag == TypeTags.INT) {
            symbol = createCastOperatorSymbol(actualType, expType, true);
        } else if (isValueType(expType) &&
                (actualType.tag == TypeTags.FINITE ||
                         (actualType.tag == TypeTags.UNION && ((BUnionType) actualType).getMemberTypes().stream()
                                 .anyMatch(type -> type.tag == TypeTags.FINITE && isAssignable(type, expType))))) {
            if (expType.tag != TypeTags.INT && expType.tag != TypeTags.BYTE && expType.tag != TypeTags.FLOAT
                    && expType.tag != TypeTags.STRING && expType.tag != TypeTags.BOOLEAN) {
                // for decimal or nil, no cast is required
                return symbol;
            }
            symbol = createCastOperatorSymbol(symTable.anyType, expType, true);
        } else if (expType.tag == TypeTags.ERROR
                && (actualType.tag == TypeTags.UNION
                && isAllErrorMembers((BUnionType) actualType))) {
            symbol = createCastOperatorSymbol(symTable.anyType, symTable.errorType, true);

        }
        return symbol;
    }

    private boolean isAllErrorMembers(BUnionType actualType) {
        return actualType.getMemberTypes().stream().allMatch(t -> isAssignable(t, symTable.errorType));
    }

    public void setImplicitCastExpr(BLangExpression expr, BType actualType, BType expType) {
        BSymbol symbol = getImplicitCastOpSymbol(actualType, expType);

        if (symbol == symTable.notFoundSymbol) {
            return;
        }

        BCastOperatorSymbol conversionSym = (BCastOperatorSymbol) symbol;
        BLangTypeConversionExpr implicitConversionExpr =
                (BLangTypeConversionExpr) TreeBuilder.createTypeConversionNode();
        implicitConversionExpr.pos = expr.pos;
        implicitConversionExpr.expr = expr.impConversionExpr == null ? expr : expr.impConversionExpr;
        implicitConversionExpr.type = expType;
        implicitConversionExpr.targetType = expType;
        implicitConversionExpr.conversionSymbol = conversionSym;
        expr.impConversionExpr = implicitConversionExpr;
    }

    public BSymbol getCastOperator(BLangExpression expr, BType sourceType, BType targetType) {
        if (sourceType.tag == TypeTags.SEMANTIC_ERROR || targetType.tag == TypeTags.SEMANTIC_ERROR ||
                sourceType == targetType) {
            return createCastOperatorSymbol(sourceType, targetType, true);
        }
        BSymbol bSymbol = symResolver.resolveTypeCastOperator(expr, sourceType, targetType);
        if (bSymbol != null && bSymbol != symTable.notFoundSymbol) {
            return bSymbol;
        }
        return createCastOperatorSymbol(sourceType, targetType, true);
    }

    public BSymbol getConversionOperator(BType sourceType, BType targetType) {
        if (sourceType.tag == TypeTags.SEMANTIC_ERROR || targetType.tag == TypeTags.SEMANTIC_ERROR ||
                sourceType == targetType) {
            return createCastOperatorSymbol(sourceType, targetType, true);
        }
        BSymbol bSymbol = symResolver.resolveOperator(Names.CONVERSION_OP, Lists.of(sourceType, targetType));
        if (bSymbol != null && bSymbol != symTable.notFoundSymbol) {
            return bSymbol;
        }
        return symResolver.resolveOperator(Names.CAST_OP, Lists.of(sourceType, targetType));
    }

    BSymbol getTypeCastOperator(BLangExpression expr, BType sourceType, BType targetType) {
        if (sourceType.tag == TypeTags.SEMANTIC_ERROR || targetType.tag == TypeTags.SEMANTIC_ERROR ||
                sourceType == targetType) {
            return createCastOperatorSymbol(sourceType, targetType, true);
        }

        if (isAssignable(sourceType, targetType)) {
            if (isValueType(sourceType) || isValueType(targetType)) {
                return getImplicitCastOpSymbol(sourceType, targetType);
            }
            return createCastOperatorSymbol(sourceType, targetType, true);
        }

        if (isAssignable(targetType, sourceType)) {
            if (isValueType(sourceType)) {
                setImplicitCastExpr(expr, sourceType, symTable.anyType);
            }
            return symResolver.createTypeCastSymbol(sourceType, targetType);
        }

        if (containsNumericType(targetType)) {
            BSymbol symbol = symResolver.getNumericConversionOrCastSymbol(expr, sourceType, targetType);
            if (symbol != symTable.notFoundSymbol) {
                return symbol;
            }
        }

        boolean validTypeCast = false;

        if (sourceType.tag == TypeTags.UNION) {
            if (getTypeForUnionTypeMembersAssignableToType((BUnionType) sourceType, targetType)
                    != symTable.semanticError) {
                // string|typedesc v1 = "hello world";
                // json|table<Foo> v2 = <json|table<Foo>> v1;
                validTypeCast = true;
            }
        }

        if (targetType.tag == TypeTags.UNION) {
            if (getTypeForUnionTypeMembersAssignableToType((BUnionType) targetType, sourceType)
                    != symTable.semanticError) {
                // string|int v1 = "hello world";
                // string|boolean v2 = <string|boolean> v1;
                validTypeCast = true;
            }
        }

        if (sourceType.tag == TypeTags.FINITE) {
            if (getTypeForFiniteTypeValuesAssignableToType((BFiniteType) sourceType, targetType)
                    != symTable.semanticError) {
                validTypeCast = true;
            }
        }

        if (targetType.tag == TypeTags.FINITE) {
            if (getTypeForFiniteTypeValuesAssignableToType((BFiniteType) targetType, sourceType)
                    != symTable.semanticError) {
                validTypeCast = true;
            }
        }

        if (validTypeCast) {
            if (isValueType(sourceType)) {
                setImplicitCastExpr(expr, sourceType, symTable.anyType);
            }
            return symResolver.createTypeCastSymbol(sourceType, targetType);
        }
        return symTable.notFoundSymbol;
    }

    public BType getElementType(BType type) {
        if (type.tag != TypeTags.ARRAY) {
            return type;
        }

        return getElementType(((BArrayType) type).getElementType());
    }

    public boolean checkListenerCompatibility(BType type) {
        if (type.tag != TypeTags.OBJECT) {
            return false;
        }
        final BSymbol bSymbol = symTable.langObjectModuleSymbol.scope.lookup(Names.LISTENER).symbol;
        if (bSymbol == symTable.notFoundSymbol || bSymbol.type.tag != TypeTags.OBJECT) {
            throw new AssertionError("Listener object not defined.");
        }
        BObjectType rhsType = (BObjectType) type;
        BObjectType lhsType = (BObjectType) bSymbol.type;

        BStructureTypeSymbol lhsStructSymbol = (BStructureTypeSymbol) lhsType.tsymbol;
        List<BAttachedFunction> lhsFuncs = lhsStructSymbol.attachedFuncs;
        List<BAttachedFunction> rhsFuncs = ((BStructureTypeSymbol) rhsType.tsymbol).attachedFuncs;

        int lhsAttachedFuncCount = lhsStructSymbol.initializerFunc != null ? lhsFuncs.size() - 1 : lhsFuncs.size();
        if (lhsAttachedFuncCount > rhsFuncs.size()) {
            return false;
        }

        for (BAttachedFunction lhsFunc : lhsFuncs) {
            if (lhsFunc == lhsStructSymbol.initializerFunc) {
                continue;
            }

            if (!Symbols.isPublic(lhsFunc.symbol)) {
                return false;
            }

            BAttachedFunction rhsFunc = getMatchingInvokableType(rhsFuncs, lhsFunc, new HashSet<>());
            if (rhsFunc == null || !Symbols.isPublic(rhsFunc.symbol)) {
                return false;
            }
        }
        return true;
    }

    public boolean isValidErrorDetailType(BType detailType) {
        switch (detailType.tag) {
            case TypeTags.MAP:
            case TypeTags.RECORD:
                return isAssignable(detailType, symTable.detailType);

        }
        return false;
    }

    // private methods

    private BCastOperatorSymbol createCastOperatorSymbol(BType sourceType,
                                                         BType targetType,
                                                         boolean safe) {
        return Symbols.createCastOperatorSymbol(sourceType, targetType, symTable.errorType,
                false, safe, null, null);
    }

    private boolean isNullable(BType fieldType) {
        return fieldType.isNullable();
    }

    private class BSameTypeVisitor implements BTypeVisitor<BType, Boolean> {

        Set<TypePair> unresolvedTypes;

        BSameTypeVisitor(Set<TypePair> unresolvedTypes) {
            this.unresolvedTypes = unresolvedTypes;
        }

        @Override
        public Boolean visit(BType t, BType s) {

            if (t == s) {
                return true;
            }
            switch (t.tag) {
                case TypeTags.INT:
                case TypeTags.BYTE:
                case TypeTags.FLOAT:
                case TypeTags.DECIMAL:
                case TypeTags.STRING:
                case TypeTags.BOOLEAN:
                case TypeTags.ANY:
                case TypeTags.ANYDATA:
                    return t.tag == s.tag
                            && (TypeParamAnalyzer.isTypeParam(t) || TypeParamAnalyzer.isTypeParam(s));
                default:
                    break;
            }
            return false;

        }

        @Override
        public Boolean visit(BBuiltInRefType t, BType s) {
            return t == s;
        }

        @Override
        public Boolean visit(BAnyType t, BType s) {
            return t == s;
        }

        @Override
        public Boolean visit(BAnydataType t, BType s) {
            return t == s;
        }

        @Override
        public Boolean visit(BMapType t, BType s) {
            if (s.tag != TypeTags.MAP) {
                return false;
            }
            // At this point both source and target types are of map types. Inorder to be equal in type as whole
            // constraints should be in equal type.
            BMapType sType = ((BMapType) s);
            return isSameType(sType.constraint, t.constraint, this.unresolvedTypes);
        }

        @Override
        public Boolean visit(BFutureType t, BType s) {
            return s.tag == TypeTags.FUTURE && t.constraint.tag == ((BFutureType) s).constraint.tag;
        }

        @Override
        public Boolean visit(BXMLType t, BType s) {
            return visit((BBuiltInRefType) t, s);
        }

        @Override
        public Boolean visit(BJSONType t, BType s) {
            return s.tag == TypeTags.JSON;
        }

        @Override
        public Boolean visit(BArrayType t, BType s) {
            return s.tag == TypeTags.ARRAY && checkArrayEquality(s, t, new HashSet<>());
        }

        @Override
        public Boolean visit(BObjectType t, BType s) {
            if (t == s) {
                return true;
            }

            if (s.tag != TypeTags.OBJECT) {
                return false;
            }

            return t.tsymbol.pkgID.equals(s.tsymbol.pkgID) && t.tsymbol.name.equals(s.tsymbol.name);
        }

        @Override
        public Boolean visit(BRecordType t, BType s) {

            if (t == s) {
                return true;
            }
            if (s.tag != TypeTags.RECORD) {
                return false;
            }
            BRecordType source = (BRecordType) s;

            if (source.fields.size() != t.fields.size()) {
                return false;
            }

            boolean notSameType = source.fields
                    .stream()
                    .map(fs -> t.fields.stream()
                            .anyMatch(ft -> fs.name.equals(ft.name)
                                    && isSameType(fs.type, ft.type, this.unresolvedTypes)
                                    && hasSameOptionalFlag(fs.symbol, ft.symbol)))
                    .anyMatch(foundSameType -> !foundSameType);
            if (notSameType) {
                return false;
            }
            return isSameType(source.restFieldType, t.restFieldType, unresolvedTypes);
        }

        private boolean hasSameOptionalFlag(BVarSymbol s, BVarSymbol t) {
            return ((s.flags & Flags.OPTIONAL) ^ (t.flags & Flags.OPTIONAL)) != Flags.OPTIONAL;
        }

        @Override
        public Boolean visit(BTableType t, BType s) {
            return t == s;
        }

        public Boolean visit(BTupleType t, BType s) {
            if (s.tag != TypeTags.TUPLE) {
                return false;
            }
            BTupleType source = (BTupleType) s;
            if (source.tupleTypes.size() != t.tupleTypes.size()) {
                return false;
            }
            for (int i = 0; i < source.tupleTypes.size(); i++) {
                if (t.getTupleTypes().get(i) == symTable.noType) {
                    continue;
                }
                if (!isSameType(source.getTupleTypes().get(i), t.tupleTypes.get(i), this.unresolvedTypes)) {
                    return false;
                }
            }
            return true;
        }

        @Override
        public Boolean visit(BStreamType t, BType s) {
            return t == s;
        }

        @Override
        public Boolean visit(BInvokableType t, BType s) {
            return s.tag == TypeTags.INVOKABLE && isSameFunctionType((BInvokableType) s, t, new HashSet<>());
        }

        @Override
        public Boolean visit(BUnionType tUnionType, BType s) {
            if (s.tag != TypeTags.UNION) {
                return false;
            }

            BUnionType sUnionType = (BUnionType) s;

            if (sUnionType.getMemberTypes().size()
                    != tUnionType.getMemberTypes().size()) {
                return false;
            }

            Set<BType> sourceTypes = new LinkedHashSet<>(sUnionType.getMemberTypes());
            Set<BType> targetTypes = new LinkedHashSet<>(tUnionType.getMemberTypes());

            boolean notSameType = sourceTypes
                    .stream()
                    .map(sT -> targetTypes
                            .stream()
                            .anyMatch(it -> isSameType(it, sT, this.unresolvedTypes)))
                    .anyMatch(foundSameType -> !foundSameType);
            return !notSameType;
        }

        @Override
        public Boolean visit(BSemanticErrorType t, BType s) {
            return true;
        }

        @Override
        public Boolean visit(BErrorType t, BType s) {
            if (s.tag != TypeTags.ERROR) {
                return false;
            }
            BErrorType source = (BErrorType) s;

            if (!isSameType(source.reasonType, t.reasonType, this.unresolvedTypes)) {
                return false;
            }

            if (source.detailType == t.detailType) {
                return true;
            }

            return isSameType(source.detailType, t.detailType, this.unresolvedTypes);
        }

        @Override
        public Boolean visit(BServiceType t, BType s) {
            return t == s || t.tag == s.tag;
        }

        @Override
        public Boolean visit(BTypedescType t, BType s) {

            if (s.tag != TypeTags.TYPEDESC) {
                return false;
            }
            BTypedescType sType = ((BTypedescType) s);
            return isSameType(sType.constraint, t.constraint, this.unresolvedTypes);
        }


        @Override
        public Boolean visit(BFiniteType t, BType s) {

            return s == t;
        }

    };

    private boolean checkFieldEquivalency(BRecordType lhsType, BRecordType rhsType, Set<TypePair> unresolvedTypes) {
        Map<Name, BField> rhsFields = rhsType.fields.stream().collect(Collectors.toMap(BField::getName, f -> f));

        // Check if the RHS record has corresponding fields to those of the LHS record.
        for (BField lhsField : lhsType.fields) {
            BField rhsField = rhsFields.get(lhsField.name);

            // There should be a corresponding RHS field
            if (rhsField == null) {
                return false;
            }

            // If LHS field is required, so should the RHS field
            if (!Symbols.isOptional(lhsField.symbol) && Symbols.isOptional(rhsField.symbol)) {
                return false;
            }

            // The corresponding RHS field should be assignable to the LHS field.
            if (!isAssignable(rhsField.type, lhsField.type, unresolvedTypes)) {
                return false;
            }

            rhsFields.remove(lhsField.name);
        }

        // If there are any remaining RHS fields, the types of those should be assignable to the rest field type of
        // the LHS record.
        return rhsFields.entrySet().stream().allMatch(
                fieldEntry -> isAssignable(fieldEntry.getValue().type, lhsType.restFieldType, unresolvedTypes));
    }

    private BAttachedFunction getMatchingInvokableType(List<BAttachedFunction> rhsFuncList, BAttachedFunction lhsFunc,
                                                       Set<TypePair> unresolvedTypes) {
        return rhsFuncList.stream()
                .filter(rhsFunc -> lhsFunc.funcName.equals(rhsFunc.funcName))
                .filter(rhsFunc -> isFunctionTypeAssignable(rhsFunc.type, lhsFunc.type, unresolvedTypes))
                .findFirst()
                .orElse(null);
    }

    private boolean isInSameVisibilityRegion(BSymbol lhsSym, BSymbol rhsSym) {
        if (Symbols.isPrivate(lhsSym)) {
            return Symbols.isPrivate(rhsSym) && lhsSym.pkgID.equals(rhsSym.pkgID)
                    && lhsSym.owner.name.equals(rhsSym.owner.name);
        } else if (Symbols.isPublic(lhsSym)) {
            return Symbols.isPublic(rhsSym);
        }
        return !Symbols.isPrivate(rhsSym) && !Symbols.isPublic(rhsSym) && lhsSym.pkgID.equals(rhsSym.pkgID);
    }

    private boolean isAssignableToUnionType(BType source, BType target, Set<TypePair> unresolvedTypes) {
        Set<BType> sourceTypes = new LinkedHashSet<>();
        Set<BType> targetTypes = new LinkedHashSet<>();

        if (source.tag == TypeTags.UNION) {
            BUnionType sourceUnionType = (BUnionType) source;
            sourceTypes.addAll(sourceUnionType.getMemberTypes());
        } else {
            sourceTypes.add(source);
        }

        if (target.tag == TypeTags.UNION) {
            BUnionType targetUnionType = (BUnionType) target;
            targetTypes.addAll(targetUnionType.getMemberTypes());
        } else {
            targetTypes.add(target);
        }

        return sourceTypes.stream()
                .allMatch(s -> (targetTypes.stream()
                                        .anyMatch(t -> isAssignable(s, t, unresolvedTypes))) ||
                        (s.tag == TypeTags.FINITE  && isAssignable(s, target, unresolvedTypes)));
    }

    private boolean isFiniteTypeAssignable(BFiniteType finiteType, BType targetType, Set<TypePair> unresolvedTypes) {
        if (targetType.tag == TypeTags.FINITE) {
            return finiteType.getValueSpace().stream()
                    .allMatch(expression -> isAssignableToFiniteType(targetType, (BLangLiteral) expression));
        }

        if (targetType.tag == TypeTags.UNION) {
            List<BType> unionMemberTypes = getAllTypes(targetType);
            return finiteType.getValueSpace().stream()
                    .allMatch(valueExpr ->  unionMemberTypes.stream()
                            .anyMatch(targetMemType -> targetMemType.tag == TypeTags.FINITE ?
                                    isAssignableToFiniteType(targetMemType, (BLangLiteral) valueExpr) :
                                    isAssignable(valueExpr.type, targetType, unresolvedTypes)));
        }

        return finiteType.getValueSpace().stream()
                .allMatch(expression -> isAssignable(expression.type, targetType, unresolvedTypes));
    }

    boolean isAssignableToFiniteType(BType type, BLangLiteral literalExpr) {
        if (type.tag != TypeTags.FINITE) {
            return false;
        }

        BFiniteType expType = (BFiniteType) type;
        return expType.getValueSpace().stream().anyMatch(memberLiteral -> {
            if (((BLangLiteral) memberLiteral).value == null) {
                return literalExpr.value == null;
            }
            // Check whether the literal that needs to be tested is assignable to any of the member literal in the
            // value space.
            return checkLiteralAssignabilityBasedOnType((BLangLiteral) memberLiteral, literalExpr);
        });
    }

    /**
     * Method to check the literal assignability based on the types of the literals. For numeric literals the
     * assignability depends on the equivalency of the literals. If the candidate literal could either be a simple
     * literal or a constant. In case of a constant, it is assignable to the base literal if and only if both
     * literals have same type and equivalent values.
     *
     * @param baseLiteral      Literal based on which we check the assignability.
     * @param candidateLiteral Literal to be tested whether it is assignable to the base literal or not.
     * @return true if assignable; false otherwise.
     */
    boolean checkLiteralAssignabilityBasedOnType(BLangLiteral baseLiteral, BLangLiteral candidateLiteral) {
        // Different literal kinds.
        if (baseLiteral.getKind() != candidateLiteral.getKind()) {
            return false;
        }
        Object baseValue = baseLiteral.value;
        Object candidateValue = candidateLiteral.value;
        int candidateTypeTag = candidateLiteral.type.tag;

        // Numeric literal assignability is based on assignable type and numeric equivalency of values.
        // If the base numeric literal is,
        // (1) byte: we can assign byte or a int simple literal (Not an int constant) with the same value.
        // (2) int: we can assign int literal or int constants with the same value.
        // (3) float: we can assign int simple literal(Not an int constant) or a float literal/constant with same value.
        // (4) decimal: we can assign int simple literal or float simple literal (Not int/float constants) or decimal
        // with the same value.
        switch (baseLiteral.type.tag) {
            case TypeTags.BYTE:
                if (candidateTypeTag == TypeTags.BYTE || (candidateTypeTag == TypeTags.INT &&
                        !candidateLiteral.isConstant && isByteLiteralValue((Long) candidateValue))) {
                    return ((Number) baseValue).longValue() == ((Number) candidateValue).longValue();
                }
                break;
            case TypeTags.INT:
                if (candidateTypeTag == TypeTags.INT) {
                    return ((Number) baseValue).longValue() == ((Number) candidateValue).longValue();
                }
                break;
            case TypeTags.FLOAT:
                String baseValueStr = String.valueOf(baseValue);
                String originalValue = baseLiteral.originalValue != null ? baseLiteral.originalValue : baseValueStr;
                if (NumericLiteralSupport.isDecimalDiscriminated(originalValue)) {
                    return false;
                }
                double baseDoubleVal = Double.parseDouble(baseValueStr);
                double candidateDoubleVal;
                if (candidateTypeTag == TypeTags.INT && !candidateLiteral.isConstant) {
                    candidateDoubleVal = ((Long) candidateValue).doubleValue();
                    return baseDoubleVal == candidateDoubleVal;
                } else if (candidateTypeTag == TypeTags.FLOAT) {
                    candidateDoubleVal = Double.parseDouble(String.valueOf(candidateValue));
                    return baseDoubleVal == candidateDoubleVal;
                }
                break;
            case TypeTags.DECIMAL:
                BigDecimal baseDecimalVal = NumericLiteralSupport.parseBigDecimal(baseValue);
                BigDecimal candidateDecimalVal;
                if (candidateTypeTag == TypeTags.INT && !candidateLiteral.isConstant) {
                    candidateDecimalVal = new BigDecimal((long) candidateValue, MathContext.DECIMAL128);
                    return baseDecimalVal.compareTo(candidateDecimalVal) == 0;
                } else if (candidateTypeTag == TypeTags.FLOAT && !candidateLiteral.isConstant ||
                        candidateTypeTag == TypeTags.DECIMAL) {
                    if (NumericLiteralSupport.isFloatDiscriminated(String.valueOf(candidateValue))) {
                        return false;
                    }
                    candidateDecimalVal = NumericLiteralSupport.parseBigDecimal(candidateValue);
                    return baseDecimalVal.compareTo(candidateDecimalVal) == 0;
                }
                break;
            default:
                // Non-numeric literal kind.
                return baseValue.equals(candidateValue);
        }
        return false;
    }

    boolean isByteLiteralValue(Long longObject) {
        return (longObject.intValue() >= BBYTE_MIN_VALUE && longObject.intValue() <= BBYTE_MAX_VALUE);
    }

    /**
     * Method to retrieve a type representing all the values in the value space of a finite type that are assignable to
     * the target type.
     *
     * @param finiteType the finite type
     * @param targetType the target type
     * @return           a new finite type if at least one value in the value space of the specified finiteType is
     *                      assignable to targetType (the same if all are assignable), else semanticError
     */
    BType getTypeForFiniteTypeValuesAssignableToType(BFiniteType finiteType, BType targetType) {
        // finiteType - type Foo "foo";
        // targetType - type FooBar "foo"|"bar";
        if (isAssignable(finiteType, targetType)) {
            return finiteType;
        }

        // Identify all the values from the value space of the finite type that are assignable to the target type.
        // e.g., finiteType - type Foo "foo"|1 ;
        Set<BLangExpression> matchingValues = finiteType.getValueSpace().stream()
                .filter(
                        // case I: targetType - string ("foo" is assignable to string)
                        // case II: targetType - type Bar "foo"|"baz" ; ("foo" is assignable to Bar)
                        expr -> isAssignable(expr.type, targetType) ||
                                isAssignableToFiniteType(targetType, (BLangLiteral) expr) ||
                                // type FooVal "foo";
                                // case III:  targetType - boolean|FooVal ("foo" is assignable to FooVal)
                                (targetType.tag == TypeTags.UNION &&
                                         ((BUnionType) targetType).getMemberTypes().stream()
                                                 .filter(memType ->  memType.tag == TypeTags.FINITE)
                                                 .anyMatch(filteredType -> isAssignableToFiniteType(filteredType,
                                                                                            (BLangLiteral) expr))))
                .collect(Collectors.toSet());

        if (matchingValues.isEmpty()) {
            return symTable.semanticError;
        }

        // Create a new finite type representing the assignable values.
        BTypeSymbol finiteTypeSymbol = Symbols.createTypeSymbol(SymTag.FINITE_TYPE, finiteType.tsymbol.flags,
                                                                names.fromString("$anonType$" + finiteTypeCount++),
                                                                finiteType.tsymbol.pkgID, null,
                                                                finiteType.tsymbol.owner);
        BFiniteType intersectingFiniteType = new BFiniteType(finiteTypeSymbol, matchingValues);
        finiteTypeSymbol.type = intersectingFiniteType;
        return intersectingFiniteType;
    }

    /**
     * Method to retrieve a type representing all the member types of a union type that are assignable to
     * the target type.
     *
     * @param unionType  the union type
     * @param targetType the target type
     * @return           a single type or a new union type if at least one member type of the union type is
     *                      assignable to targetType, else semanticError
     */
    BType getTypeForUnionTypeMembersAssignableToType(BUnionType unionType, BType targetType) {
        List<BType> intersection = new LinkedList<>();

        // type FooOne "foo"|1;
        // type FooBar "foo"|"bar";
        // unionType - boolean|FooOne, targetType - boolean|FooBar
        unionType.getMemberTypes().forEach(memType -> {
            if (memType.tag == TypeTags.FINITE) {
                // since "foo" of FooOne is assignable to FooBar, a new finite type of only "foo" would be returned
                BType finiteTypeWithMatches = getTypeForFiniteTypeValuesAssignableToType((BFiniteType) memType,
                                                                                         targetType);
                if (finiteTypeWithMatches != symTable.semanticError) {
                    intersection.add(finiteTypeWithMatches);
                }
            } else {
                // boolean is assignable to boolean, thus boolean is added as a member type
                if (isAssignable(memType, targetType)) {
                    intersection.add(memType);
                }
            }
        });

        if (intersection.isEmpty()) {
            return symTable.semanticError;
        }

        if (intersection.size() == 1) {
            return intersection.get(0);
        } else {
            return BUnionType.create(null, new LinkedHashSet<>(intersection));
        }
    }

    boolean validEqualityIntersectionExists(BType lhsType, BType rhsType) {
        if (!lhsType.isPureType() || !rhsType.isPureType()) {
            return false;
        }

        if (isAssignable(lhsType, rhsType) || isAssignable(rhsType, lhsType)) {
            return true;
        }

        Set<BType> lhsTypes = expandAndGetMemberTypesRecursive(lhsType);
        Set<BType> rhsTypes = expandAndGetMemberTypesRecursive(rhsType);
        return equalityIntersectionExists(lhsTypes, rhsTypes);
    }

    private boolean equalityIntersectionExists(Set<BType> lhsTypes, Set<BType> rhsTypes) {
        if ((lhsTypes.contains(symTable.anydataType) &&
                     rhsTypes.stream().anyMatch(type -> type.tag != TypeTags.ERROR)) ||
                (rhsTypes.contains(symTable.anydataType) &&
                         lhsTypes.stream().anyMatch(type -> type.tag != TypeTags.ERROR))) {
            return true;
        }

        boolean matchFound = lhsTypes
                .stream()
                .anyMatch(s -> rhsTypes
                        .stream()
                        .anyMatch(t -> isSameType(s, t)));

        if (!matchFound) {
            matchFound = equalityIntersectionExistsForComplexTypes(lhsTypes, rhsTypes);
        }

        return matchFound;
    }

    /**
     * Retrieves member types of the specified type, expanding maps/arrays of/constrained by unions types to individual
     * maps/arrays.
     *
     * e.g., (string|int)[] would cause three entries as string[], int[], (string|int)[]
     *
     * @param bType the type for which member types needs to be identified
     * @return  a set containing all the retrieved member types
     */
    public Set<BType> expandAndGetMemberTypesRecursive(BType bType) {
        Set<BType> memberTypes = new LinkedHashSet<>();
        switch (bType.tag) {
            case TypeTags.BYTE:
            case TypeTags.INT:
                memberTypes.add(symTable.intType);
                memberTypes.add(symTable.byteType);
                break;
            case TypeTags.FINITE:
                BFiniteType expType = (BFiniteType) bType;
                expType.getValueSpace().forEach(value -> {
                    memberTypes.add(value.type);
                });
                break;
            case TypeTags.UNION:
                BUnionType unionType = (BUnionType) bType;
                unionType.getMemberTypes().forEach(member -> {
                    memberTypes.addAll(expandAndGetMemberTypesRecursive(member));
                });
                break;
            case TypeTags.ARRAY:
                BType arrayElementType = ((BArrayType) bType).getElementType();

                // add an unsealed array to allow comparison between closed and open arrays
                // TODO: 10/16/18 improve this, since it will allow comparison between sealed arrays of different sizes
                if (((BArrayType) bType).getSize() != -1) {
                    memberTypes.add(new BArrayType(arrayElementType));
                }

                if (arrayElementType.tag == TypeTags.UNION) {
                    Set<BType> elementUnionTypes = expandAndGetMemberTypesRecursive(arrayElementType);
                    elementUnionTypes.forEach(elementUnionType -> {
                        memberTypes.add(new BArrayType(elementUnionType));
                    });
                }
                memberTypes.add(bType);
                break;
            case TypeTags.MAP:
                BType mapConstraintType = ((BMapType) bType).getConstraint();
                if (mapConstraintType.tag == TypeTags.UNION) {
                    Set<BType> constraintUnionTypes = expandAndGetMemberTypesRecursive(mapConstraintType);
                    constraintUnionTypes.forEach(constraintUnionType -> {
                        memberTypes.add(new BMapType(TypeTags.MAP, constraintUnionType, symTable.mapType.tsymbol));
                    });
                }
                memberTypes.add(bType);
                break;
            default:
                memberTypes.add(bType);
        }
        return memberTypes;
    }

    private boolean tupleIntersectionExists(BTupleType lhsType, BTupleType rhsType) {
        if (lhsType.getTupleTypes().size() != rhsType.getTupleTypes().size()) {
            return false;
        }

        List<BType> lhsMemberTypes = lhsType.getTupleTypes();
        List<BType> rhsMemberTypes = rhsType.getTupleTypes();

        for (int i = 0; i < lhsType.getTupleTypes().size(); i++) {
            if (!equalityIntersectionExists(expandAndGetMemberTypesRecursive(lhsMemberTypes.get(i)),
                                            expandAndGetMemberTypesRecursive(rhsMemberTypes.get(i)))) {
                return false;
            }
        }
        return true;
    }

    private boolean equalityIntersectionExistsForComplexTypes(Set<BType> lhsTypes, Set<BType> rhsTypes) {
        for (BType lhsMemberType : lhsTypes) {
            switch (lhsMemberType.tag) {
                case TypeTags.INT:
                case TypeTags.STRING:
                case TypeTags.FLOAT:
                case TypeTags.DECIMAL:
                case TypeTags.BOOLEAN:
                case TypeTags.NIL:
                    if (rhsTypes.stream().anyMatch(rhsMemberType -> rhsMemberType.tag == TypeTags.JSON)) {
                        return true;
                    }
                    break;
                case TypeTags.JSON:
                    if (jsonEqualityIntersectionExists(rhsTypes)) {
                        return true;
                    }
                    break;
                // When expanding members for tuples, arrays and maps, set isValueDeepEquality to true, to allow
                // comparison between JSON lists/maps and primitive lists/maps since they are all reference types
                case TypeTags.TUPLE:
                    if (rhsTypes.stream().anyMatch(
                            rhsMemberType -> rhsMemberType.tag == TypeTags.TUPLE &&
                                    tupleIntersectionExists((BTupleType) lhsMemberType, (BTupleType) rhsMemberType))) {
                        return true;
                    }

                    if (rhsTypes.stream().anyMatch(
                            rhsMemberType -> rhsMemberType.tag == TypeTags.ARRAY &&
                                    arrayTupleEqualityIntersectionExists((BArrayType) rhsMemberType,
                                                                         (BTupleType) lhsMemberType))) {
                        return true;
                    }
                    break;
                case TypeTags.ARRAY:
                    if (rhsTypes.stream().anyMatch(
                            rhsMemberType -> rhsMemberType.tag == TypeTags.ARRAY &&
                                    equalityIntersectionExists(
                                            expandAndGetMemberTypesRecursive(((BArrayType) lhsMemberType).eType),
                                            expandAndGetMemberTypesRecursive(((BArrayType) rhsMemberType).eType)))) {
                        return true;
                    }

                    if (rhsTypes.stream().anyMatch(
                            rhsMemberType -> rhsMemberType.tag == TypeTags.TUPLE &&
                                    arrayTupleEqualityIntersectionExists((BArrayType) lhsMemberType,
                                                                         (BTupleType) rhsMemberType))) {
                        return true;
                    }
                    break;
                case TypeTags.MAP:
                    if (rhsTypes.stream().anyMatch(
                            rhsMemberType -> rhsMemberType.tag == TypeTags.MAP &&
                                    equalityIntersectionExists(
                                            expandAndGetMemberTypesRecursive(((BMapType) lhsMemberType).constraint),
                                            expandAndGetMemberTypesRecursive(((BMapType) rhsMemberType).constraint)))) {
                        return true;
                    }

                    if (!isAssignable(((BMapType) lhsMemberType).constraint, symTable.errorType) &&
                            rhsTypes.stream().anyMatch(rhsMemberType -> rhsMemberType.tag == TypeTags.JSON)) {
                        // at this point it is guaranteed that the map is anydata
                        return true;
                    }

                    if (rhsTypes.stream().anyMatch(
                            rhsMemberType -> rhsMemberType.tag == TypeTags.RECORD &&
                                    mapRecordEqualityIntersectionExists((BMapType) lhsMemberType,
                                                                        (BRecordType) rhsMemberType))) {
                        return true;
                    }
                    break;
                case TypeTags.OBJECT:
                case TypeTags.RECORD:
                    if (rhsTypes.stream().anyMatch(
                            rhsMemberType -> checkStructEquivalency(rhsMemberType, lhsMemberType) ||
                                    checkStructEquivalency(lhsMemberType, rhsMemberType))) {
                        return true;
                    }

                    if (rhsTypes.stream().anyMatch(
                            rhsMemberType -> rhsMemberType.tag == TypeTags.RECORD &&
                                    recordEqualityIntersectionExists((BRecordType) lhsMemberType,
                                                                     (BRecordType) rhsMemberType))) {
                        return true;
                    }

                    if (rhsTypes.stream().anyMatch(rhsMemberType -> rhsMemberType.tag == TypeTags.JSON) &&
                            jsonEqualityIntersectionExists(expandAndGetMemberTypesRecursive(lhsMemberType))) {
                        return true;
                    }

                    if (rhsTypes.stream().anyMatch(
                            rhsMemberType -> rhsMemberType.tag == TypeTags.MAP &&
                                    mapRecordEqualityIntersectionExists((BMapType) rhsMemberType,
                                                                        (BRecordType) lhsMemberType))) {
                        return true;
                    }
                    break;
            }
        }
        return false;
    }

    private boolean arrayTupleEqualityIntersectionExists(BArrayType arrayType, BTupleType tupleType) {
        Set<BType> elementTypes = expandAndGetMemberTypesRecursive(arrayType.eType);

        return tupleType.tupleTypes.stream()
                .allMatch(tupleMemType -> equalityIntersectionExists(elementTypes,
                                                                     expandAndGetMemberTypesRecursive(tupleMemType)));
    }

    private boolean recordEqualityIntersectionExists(BRecordType lhsType, BRecordType rhsType) {
        List<BField> lhsFields = lhsType.fields;
        List<BField> rhsFields = rhsType.fields;

        List<Name> matchedFieldNames = new ArrayList<>();
        for (BField lhsField : lhsFields) {
            Optional<BField> match =
                    rhsFields.stream().filter(rhsField -> lhsField.name.equals(rhsField.name)).findFirst();

            if (match.isPresent()) {
                if (!equalityIntersectionExists(expandAndGetMemberTypesRecursive(lhsField.type),
                                                expandAndGetMemberTypesRecursive(match.get().type))) {
                    return false;
                }
                matchedFieldNames.add(lhsField.getName());
            } else {
                if (Symbols.isFlagOn(lhsField.symbol.flags, Flags.OPTIONAL)) {
                    break;
                }

                if (rhsType.sealed) {
                    return false;
                }

                if (!equalityIntersectionExists(expandAndGetMemberTypesRecursive(lhsField.type),
                                                expandAndGetMemberTypesRecursive(rhsType.restFieldType))) {
                    return false;
                }
            }
        }

        for (BField rhsField : rhsFields) {
            if (matchedFieldNames.contains(rhsField.getName())) {
                continue;
            }

            if (!Symbols.isFlagOn(rhsField.symbol.flags, Flags.OPTIONAL)) {
                if (lhsType.sealed) {
                    return false;
                }

                if (!equalityIntersectionExists(expandAndGetMemberTypesRecursive(rhsField.type),
                                                expandAndGetMemberTypesRecursive(lhsType.restFieldType))) {
                    return false;
                }
            }
        }

        return true;
    }

    private boolean mapRecordEqualityIntersectionExists(BMapType mapType, BRecordType recordType) {
        Set<BType> mapConstrTypes = expandAndGetMemberTypesRecursive(mapType.getConstraint());

        return recordType.fields.stream()
                .allMatch(field -> Symbols.isFlagOn(field.symbol.flags, Flags.OPTIONAL) ||
                        equalityIntersectionExists(mapConstrTypes, expandAndGetMemberTypesRecursive(field.type)));
    }

    private boolean jsonEqualityIntersectionExists(Set<BType> typeSet) {
        for (BType type : typeSet) {
            switch (type.tag) {
                case TypeTags.MAP:
                    if (!isAssignable(((BMapType) type).constraint, symTable.errorType)) {
                        return true;
                    }
                    break;
                case TypeTags.RECORD:
                    BRecordType recordType = (BRecordType) type;
                    if (recordType.fields.stream()
                            .allMatch(field -> Symbols.isFlagOn(field.symbol.flags, Flags.OPTIONAL) ||
                                    !isAssignable(field.type, symTable.errorType))) {
                        return true;
                    }
                    break;
                default:
                    if (isAssignable(type, symTable.jsonType)) {
                        return true;
                    }
            }
        }
        return false;
    }

    public BType getRemainingType(BType originalType, BType typeToRemove) {
        switch (originalType.tag) {
            case TypeTags.UNION:
                return getRemainingType((BUnionType) originalType, getAllTypes(typeToRemove));
            case TypeTags.FINITE:
                return getRemainingType((BFiniteType) originalType, getAllTypes(typeToRemove));
            default:
                return originalType;
        }
    }

    private BType getRemainingType(BUnionType originalType, List<BType> removeTypes) {
        List<BType> remainingTypes = getAllTypes(originalType);
        removeTypes.forEach(removeType -> remainingTypes.removeIf(type -> isAssignable(type, removeType)));

        List<BType> finiteTypesToRemove = new ArrayList<>();
        List<BType> finiteTypesToAdd = new ArrayList<>();
        for (BType remainingType : remainingTypes) {
            if (remainingType.tag == TypeTags.FINITE) {
                BFiniteType finiteType = (BFiniteType) remainingType;
                finiteTypesToRemove.add(finiteType);
                BType remainingTypeWithMatchesRemoved = getRemainingType(finiteType, removeTypes);
                if (remainingTypeWithMatchesRemoved != symTable.semanticError) {
                    finiteTypesToAdd.add(remainingTypeWithMatchesRemoved);
                }
            }
        }
        remainingTypes.removeAll(finiteTypesToRemove);
        remainingTypes.addAll(finiteTypesToAdd);

        if (remainingTypes.size() == 1) {
            return remainingTypes.get(0);
        }

        if (remainingTypes.isEmpty()) {
            return symTable.semanticError;
        }

        return BUnionType.create(null, new LinkedHashSet<>(remainingTypes));
    }

    private BType getRemainingType(BFiniteType originalType, List<BType> removeTypes) {
        Set<BLangExpression> remainingValueSpace = new LinkedHashSet<>();

        for (BLangExpression valueExpr : originalType.getValueSpace()) {
            boolean matchExists = false;
            for (BType remType : removeTypes) {
                if (isAssignable(valueExpr.type, remType) ||
                        isAssignableToFiniteType(remType, (BLangLiteral) valueExpr)) {
                    matchExists = true;
                    break;
                }
            }

            if (!matchExists) {
                remainingValueSpace.add(valueExpr);
            }
        }

        if (remainingValueSpace.isEmpty()) {
            return symTable.semanticError;
        }

        BTypeSymbol finiteTypeSymbol = Symbols.createTypeSymbol(SymTag.FINITE_TYPE, originalType.tsymbol.flags,
                                                                names.fromString("$anonType$" + finiteTypeCount++),
                                                                originalType.tsymbol.pkgID, null,
                                                                originalType.tsymbol.owner);
        BFiniteType intersectingFiniteType = new BFiniteType(finiteTypeSymbol, remainingValueSpace);
        finiteTypeSymbol.type = intersectingFiniteType;
        return intersectingFiniteType;
    }

    public BType getSafeType(BType type, boolean liftNil, boolean liftError) {
        // Since JSON, ANY and ANYDATA by default contain null, we need to create a new respective type which
        // is not-nullable.
        switch (type.tag) {
            case TypeTags.JSON:
                BJSONType jsonType = (BJSONType) type;
                return new BJSONType(jsonType.tag, jsonType.tsymbol, false);
            case TypeTags.ANY:
                return new BAnyType(type.tag, type.tsymbol, false);
            case TypeTags.ANYDATA:
                return new BAnydataType(type.tag, type.tsymbol, false);
        }

        if (type.tag != TypeTags.UNION) {
            return type;
        }

        BUnionType unionType = (BUnionType) type;
        LinkedHashSet<BType> memTypes = new LinkedHashSet<>(unionType.getMemberTypes());
        BUnionType errorLiftedType = BUnionType.create(null, memTypes);

        if (liftNil) {
            errorLiftedType.remove(symTable.nilType);
        }

        if (liftError) {
            errorLiftedType.remove(symTable.errorType);
        }

        if (errorLiftedType.getMemberTypes().size() == 1) {
            return errorLiftedType.getMemberTypes().toArray(new BType[0])[0];
        }
        return errorLiftedType;
    }

    public List<BType> getAllTypes(BType type) {
        if (type.tag != TypeTags.UNION) {
            return Lists.of(type);
        }

        List<BType> memberTypes = new ArrayList<>();
        ((BUnionType) type).getMemberTypes().forEach(memberType -> memberTypes.addAll(getAllTypes(memberType)));
        return memberTypes;
    }

    public boolean isAllowedConstantType(BType type) {
        switch (type.tag) {
            case TypeTags.BOOLEAN:
            case TypeTags.INT:
            case TypeTags.BYTE:
            case TypeTags.FLOAT:
            case TypeTags.DECIMAL:
            case TypeTags.STRING:
            case TypeTags.NIL:
                return true;
            case TypeTags.MAP:
                return isAllowedConstantType(((BMapType) type).constraint);
            case TypeTags.FINITE:
                BLangExpression finiteValue = ((BFiniteType) type).getValueSpace().toArray(new BLangExpression[0])[0];
                return isAllowedConstantType(finiteValue.type);
            default:
                return false;
        }
    }

    public boolean isValidLiteral(BLangLiteral literal, BType targetType) {
        BType literalType = literal.type;
        if (literalType.tag == targetType.tag) {
            return true;
        }

        switch (targetType.tag) {
            case TypeTags.BYTE:
                return literalType.tag == TypeTags.INT && isByteLiteralValue((Long) literal.value);
            case TypeTags.DECIMAL:
                return literalType.tag == TypeTags.FLOAT || literalType.tag == TypeTags.INT;
            case TypeTags.FLOAT:
                return literalType.tag == TypeTags.INT;
            default:
                return false;
        }
    }

    /**
     * Validate if the return type of the given function is a subtype of `error?`, containing `()`.
     *
     * @param function          The function of which the return type should be validated
     * @param diagnosticCode    The code to log if the return type is invalid
     */
    public void validateErrorOrNilReturn(BLangFunction function, DiagnosticCode diagnosticCode) {
        BType returnType = function.returnTypeNode.type;

        if (returnType.tag == TypeTags.NIL) {
            return;
        }

        if (returnType.tag == TypeTags.UNION) {
            Set<BType> memberTypes = ((BUnionType) returnType).getMemberTypes();
            if (returnType.isNullable() &&
                    memberTypes.stream().allMatch(type -> type.tag == TypeTags.NIL || type.tag == TypeTags.ERROR)) {
                return;
            }
        }

        dlog.error(function.returnTypeNode.pos, diagnosticCode, function.returnTypeNode.type.toString());
    }

    /**
     * Type vector of size two, to hold the source and the target types.
     *
     * @since 0.982.0
     */
    private static class TypePair {
        BType sourceType;
        BType targetType;

        public TypePair(BType sourceType, BType targetType) {
            this.sourceType = sourceType;
            this.targetType = targetType;
        }

        @Override
        public boolean equals(Object obj) {
            if (!(obj instanceof TypePair)) {
                return false;
            }

            TypePair other = (TypePair) obj;
            return this.sourceType.equals(other.sourceType) && this.targetType.equals(other.targetType);
        }

        @Override
        public int hashCode() {
            return Objects.hash(sourceType, targetType);
        }
    }

    /**
     * A functional interface for parameterizing the type of type checking that needs to be done on the source and
     * target types.
     *
     * @since 0.995.0
     */
    private interface TypeEqualityPredicate {
        boolean test(BType source, BType target, Set<TypePair> unresolvedTypes);
    }

    public boolean hasFillerValue(BType type) {
        switch (type.tag) {
            case TypeTags.INT:
            case TypeTags.BYTE:
            case TypeTags.FLOAT:
            case TypeTags.DECIMAL:
            case TypeTags.STRING:
            case TypeTags.BOOLEAN:
            case TypeTags.JSON:
            case TypeTags.XML:
            case TypeTags.TABLE:
            case TypeTags.NIL:
            case TypeTags.ANYDATA:
            case TypeTags.MAP:
            case TypeTags.ANY:
                return true;
            case TypeTags.ARRAY:
                return checkFillerValue((BArrayType) type);
            case TypeTags.FINITE:
                return checkFillerValue((BFiniteType) type);
            case TypeTags.UNION:
                return checkFillerValue((BUnionType) type);
            case TypeTags.OBJECT:
                return checkFillerValue((BObjectType) type);
            case TypeTags.RECORD:
                return checkFillerValue((BRecordType) type);
            case TypeTags.TUPLE:
                BTupleType tupleType = (BTupleType) type;
                return tupleType.getTupleTypes().stream().allMatch(eleType -> hasFillerValue(eleType));
            default:
                return false;
        }
    }

    private boolean checkFillerValue(BObjectType type) {
        if ((type.tsymbol.flags & Flags.ABSTRACT) == Flags.ABSTRACT) {
            return false;
        }

        BAttachedFunction initFunction = ((BObjectTypeSymbol) type.tsymbol).initializerFunc;
        if (initFunction == null) {
            return true;
        }
        if (initFunction.symbol.getReturnType().getKind() != TypeKind.NIL) {
            return false;
        }

        for (BVarSymbol bVarSymbol : initFunction.symbol.getParameters()) {
            if (!bVarSymbol.defaultableParam) {
                return false;
            }
        }
        return true;
    }

    /**
     * This will handle two types. Singleton : As singleton can have one value that value should it self be a valid fill
     * value Union : 1. if nil is a member it is the fill values 2. else all the values should belong to same type and
     * the default value for that type should be a member of the union precondition : value space should have at least
     * one element
     *
     * @param type BFiniteType union or finite
     * @return boolean whether type has a valid filler value or not
     */
    private boolean checkFillerValue(BFiniteType type) {
        if (type.isNullable()) {
            return true;
        }
        if (type.getValueSpace().size() == 1) { // For singleton types, that value is the implicit initial value
            return true;
        }
        Iterator iterator = type.getValueSpace().iterator();
        BLangExpression firstElement = (BLangExpression) iterator.next();
        boolean defaultFillValuePresent = isImplicitDefaultValue(firstElement);

        while (iterator.hasNext()) {
            BLangExpression value = (BLangExpression) iterator.next();
            if (!isSameType(value.type, firstElement.type)) {
                return false;
            }
            if (!defaultFillValuePresent && isImplicitDefaultValue(value)) {
                defaultFillValuePresent = true;
            }
        }

        return defaultFillValuePresent;
    }

    private boolean checkFillerValue(BUnionType type) {
        if (type.isNullable()) {
            return true;
        }
        Iterator<BType> iterator = type.getMemberTypes().iterator();
        BType firstMember = iterator.next();
        while (iterator.hasNext()) {
            if (!isSameType(firstMember, iterator.next())) {
                return false;
            }
        }
        return isValueType(firstMember) && hasFillerValue(firstMember);
    }

    private boolean isImplicitDefaultValue(BLangExpression expression) {
        if ((expression.getKind() == NodeKind.LITERAL) || (expression.getKind() == NodeKind.NUMERIC_LITERAL)) {
            BLangLiteral literalExpression = (BLangLiteral) expression;
            BType literalExprType = literalExpression.type;
            Object value = literalExpression.getValue();
            switch (literalExprType.getKind()) {
                case INT:
                case BYTE:
                    return value.equals(Long.valueOf(0));
                case STRING:
                    return value == null || value.equals("");
                case DECIMAL:
                case FLOAT:
                    return value.equals(String.valueOf(0.0));
                case BOOLEAN:
                    return value.equals(Boolean.valueOf(false));
                case NIL:
                    return true;
                default:
                    return false;
            }
        }
        return false;
    }

    private boolean checkFillerValue(BRecordType type) {
        for (BField field : type.fields) {
            if (Symbols.isFlagOn(field.symbol.flags, Flags.OPTIONAL)) {
                continue;
            }
            if (Symbols.isFlagOn(field.symbol.flags, Flags.REQUIRED)) {
                return false;
            }
        }
        return true;
    }

    private boolean checkFillerValue(BArrayType type) {
        if (type.size == -1) {
            return true;
        }
        return hasFillerValue(type.eType);
    }
}
