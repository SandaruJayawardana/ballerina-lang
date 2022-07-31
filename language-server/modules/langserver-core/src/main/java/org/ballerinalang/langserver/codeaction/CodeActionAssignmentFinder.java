/*
 * Copyright (c) 2022, WSO2 LLC. (http://wso2.com) All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.ballerinalang.langserver.codeaction;

import io.ballerina.compiler.api.SemanticModel;
import io.ballerina.compiler.api.symbols.Symbol;
import io.ballerina.compiler.syntax.tree.AssignmentStatementNode;
import io.ballerina.compiler.syntax.tree.BlockStatementNode;
import io.ballerina.compiler.syntax.tree.CompoundAssignmentStatementNode;
import io.ballerina.compiler.syntax.tree.IfElseStatementNode;
import io.ballerina.compiler.syntax.tree.Node;
import io.ballerina.compiler.syntax.tree.NodeVisitor;
import io.ballerina.compiler.syntax.tree.NonTerminalNode;
import io.ballerina.compiler.syntax.tree.SyntaxKind;
import io.ballerina.compiler.syntax.tree.VariableDeclarationNode;
import org.ballerinalang.langserver.common.utils.PositionUtil;
import org.eclipse.lsp4j.Range;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Node visitor for extract to function code action. This will determine,
 * 1. Symbols which get assigned to some expression inside the selected range
 * 2. Symbols which are declared inside the selected range
 * 3. Nodes which are inside the selected range
 *
 * @since 2201.2.1
 */
public class CodeActionAssignmentFinder extends NodeVisitor {
    private final List<Symbol> assignmentStatementSymbols = new ArrayList<>();
    private final List<Symbol> varDeclarationSymbols = new ArrayList<>();
    private final List<Node> selectedNodes = new ArrayList<>();
    private final Range selectedRange;
    private final SemanticModel semanticModel;

    public CodeActionAssignmentFinder(Range selectedRange, SemanticModel semanticModel) {
        this.selectedRange = selectedRange;
        this.semanticModel = semanticModel;
    }
//todo change the name to maybe findAssignments?
    public void assignmentFinder(NonTerminalNode node) {
        if (node.kind() == SyntaxKind.LIST) {
            node.children().forEach(children -> {
                if (PositionUtil.isRangeWithinRange(PositionUtil.toRange(children.lineRange()), selectedRange)) {
                    selectedNodes.add(children);
                    children.accept(this);
                }
            });
        } else {
            selectedNodes.add(node);
            node.accept(this);
        }
    }

    public List<Symbol> getAssignmentStatementSymbols() {
        return assignmentStatementSymbols;
    }

    public List<Symbol> getVarDeclarationSymbols() {
        return varDeclarationSymbols;
    }

    public List<Node> getSelectedNodes() {
        return selectedNodes;
    }

    @Override
    public void visit(IfElseStatementNode node) {
        node.ifBody().accept(this);
        if (node.elseBody().isPresent()) {
            node.elseBody().get().accept(this);
        }
    }

    @Override
    public void visit(BlockStatementNode node) {
        node.statements().forEach(statementNode -> statementNode.accept(this));
    }

    @Override
    public void visit(AssignmentStatementNode node) {
        Optional<Symbol> symbol = semanticModel.symbol(node.varRef());
        if (symbol.isPresent() && !assignmentStatementSymbols.contains(symbol.get())) {
            this.assignmentStatementSymbols.add((symbol.get()));
        }
    }

    @Override
    public void visit(CompoundAssignmentStatementNode node) {
        Optional<Symbol> symbol = semanticModel.symbol(node.lhsExpression());
        if (symbol.isPresent() && !assignmentStatementSymbols.contains(symbol.get())) {
            this.assignmentStatementSymbols.add((symbol.get()));
        }
    }

    @Override
    public void visit(VariableDeclarationNode node) {
        Optional<Symbol> symbol = semanticModel.symbol(node.typedBindingPattern().bindingPattern());
        symbol.ifPresent(varDeclarationSymbols::add);
    }
}
