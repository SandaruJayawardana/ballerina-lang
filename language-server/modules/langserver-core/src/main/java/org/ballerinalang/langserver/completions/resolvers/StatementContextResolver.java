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
package org.ballerinalang.langserver.completions.resolvers;

import org.ballerinalang.langserver.common.UtilSymbolKeys;
import org.ballerinalang.langserver.common.utils.CommonUtil;
import org.ballerinalang.langserver.compiler.LSServiceOperationContext;
import org.ballerinalang.langserver.completions.CompletionKeys;
import org.ballerinalang.langserver.completions.SymbolInfo;
import org.ballerinalang.langserver.completions.util.Snippet;
import org.ballerinalang.langserver.completions.util.filters.StatementTemplateFilter;
import org.ballerinalang.langserver.completions.util.filters.SymbolFilters;
import org.ballerinalang.langserver.completions.util.sorters.ItemSorters;
import org.eclipse.lsp4j.CompletionItem;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.wso2.ballerinalang.compiler.semantics.model.symbols.BInvokableSymbol;
import org.wso2.ballerinalang.compiler.semantics.model.symbols.BSymbol;
import org.wso2.ballerinalang.util.Flags;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * Statement context resolver for resolving the items of the statement context.
 */
public class StatementContextResolver extends AbstractItemResolver {
    @Override
    public List<CompletionItem> resolveItems(LSServiceOperationContext context) {
        // Add the visible static completion items
        ArrayList<CompletionItem> completionItems = new ArrayList<>(getStaticCompletionItems(context));
        // Add the statement templates
        Either<List<CompletionItem>, List<SymbolInfo>> itemList = SymbolFilters.get(StatementTemplateFilter.class)
                .filterItems(context);
        List<SymbolInfo> filteredList = context.get(CompletionKeys.VISIBLE_SYMBOLS_KEY);

        completionItems.addAll(this.getCompletionItemList(itemList, context));
        filteredList.removeIf(CommonUtil.invalidSymbolsPredicate().or(attachedOrSelfKeywordFilter()));
        completionItems.addAll(this.getCompletionItemList(filteredList, context));
        completionItems.addAll(this.getPackagesCompletionItems(context));
        // Now we need to sort the completion items and populate the completion items specific to the scope owner
        // as an example, resource, action, function scopes are different from the if-else, while, and etc
        Class itemSorter = context.get(CompletionKeys.BLOCK_OWNER_KEY).getClass();
        ItemSorters.get(itemSorter).sortItems(context, completionItems);

        return completionItems;
    }

    private List<CompletionItem> getStaticCompletionItems(LSServiceOperationContext context) {
        boolean supportSnippet = context.get(CompletionKeys.CLIENT_CAPABILITIES_KEY)
                .getCompletionItem()
                .getSnippetSupport();

        ArrayList<CompletionItem> completionItems = new ArrayList<>();

        // Add the xmlns snippet
        CompletionItem xmlns = new CompletionItem();
        Snippet.STMT_NAMESPACE_DECLARATION.get().build(xmlns, supportSnippet);
        completionItems.add(xmlns);

        // Add the var keyword
        CompletionItem varKeyword = Snippet.KW_VAR.get().build(new CompletionItem(), supportSnippet);
        completionItems.add(varKeyword);

        // Add the error snippet
        CompletionItem error = Snippet.DEF_ERROR.get().build(new CompletionItem(), supportSnippet);
        completionItems.add(error);

        return completionItems;
    }

    private static Predicate<SymbolInfo> attachedOrSelfKeywordFilter() {
        return symbolInfo -> {
            BSymbol bSymbol = symbolInfo.getScopeEntry().symbol;
            return bSymbol instanceof BInvokableSymbol && ((bSymbol.flags & Flags.ATTACHED) == Flags.ATTACHED)
                || (UtilSymbolKeys.SELF_KEYWORD_KEY.equals(bSymbol.getName().getValue())
                && (bSymbol.owner.flags & Flags.RESOURCE) == Flags.RESOURCE);
        };
    }
}
