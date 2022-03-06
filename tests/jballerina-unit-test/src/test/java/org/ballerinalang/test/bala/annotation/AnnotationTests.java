/*
 *  Copyright (c) 2018, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 *  WSO2 Inc. licenses this file to you under the Apache License,
 *  Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package org.ballerinalang.test.bala.annotation;

import org.ballerinalang.core.model.values.BValue;
import org.ballerinalang.model.elements.PackageID;
import org.ballerinalang.model.symbols.AnnotationAttachmentSymbol;
import org.ballerinalang.test.BAssertUtil;
import org.ballerinalang.test.BCompileUtil;
import org.ballerinalang.test.BRunUtil;
import org.ballerinalang.test.CompileResult;
import org.testng.Assert;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import org.wso2.ballerinalang.compiler.semantics.model.Scope;
import org.wso2.ballerinalang.compiler.semantics.model.symbols.BAnnotationAttachmentSymbol;
import org.wso2.ballerinalang.compiler.semantics.model.symbols.BAttachedFunction;
import org.wso2.ballerinalang.compiler.semantics.model.symbols.BClassSymbol;
import org.wso2.ballerinalang.compiler.semantics.model.symbols.BInvokableSymbol;
import org.wso2.ballerinalang.compiler.semantics.model.symbols.BVarSymbol;
import org.wso2.ballerinalang.compiler.semantics.model.types.BIntersectionType;
import org.wso2.ballerinalang.compiler.semantics.model.types.BType;
import org.wso2.ballerinalang.compiler.tree.BLangConstantValue;
import org.wso2.ballerinalang.compiler.tree.BLangPackage;
import org.wso2.ballerinalang.compiler.util.Name;
import org.wso2.ballerinalang.compiler.util.Names;
import org.wso2.ballerinalang.compiler.util.TypeTags;
import org.wso2.ballerinalang.util.Lists;

import java.util.List;
import java.util.Map;

/**
 * Test cases for reading annotations.
 */
public class AnnotationTests {

    CompileResult result;

    @BeforeClass
    public void setup() {
        BCompileUtil.compileAndCacheBala("test-src/bala/test_projects/test_project");
        result = BCompileUtil.compile("test-src/bala/test_bala/annotations/annotation.bal");
    }

    @Test(description = "Test the deprecated construct from external module")
    public void testDeprecation() {
        CompileResult result = BCompileUtil.compile("test-src/bala/test_bala/annotations/deprecation_annotation.bal");
        Assert.assertEquals(result.getWarnCount(), 5);

        int i = 0;
        BAssertUtil.validateWarning(result, i++, "usage of construct 'foo:DummyObject1' is deprecated", 3, 23);
        BAssertUtil.validateWarning(result, i++, "usage of construct 'foo:Bar' is deprecated", 3, 45);
        BAssertUtil.validateWarning(result, i++, "usage of construct 'foo:C1' is deprecated", 3, 69);
        BAssertUtil.validateWarning(result, i++, "usage of construct 'obj.doThatOnObject()' is deprecated", 8
                , 5);
        BAssertUtil.validateWarning(result, i, "usage of construct 'foo:deprecated_func()' is deprecated", 9, 16);
    }

    @Test
    public void testNonBallerinaAnnotations() {
        BValue[] returns = BRunUtil.invoke(result, "testNonBallerinaAnnotations");
        Assert.assertNotNull(returns);
        Assert.assertEquals(returns.length, 1);
        Assert.assertNotNull(returns[0]);
        Assert.assertEquals(returns[0].stringValue(), "{numVal:10, textVal:\"text\", conditionVal:false, " +
                "recordVal:{nestNumVal:20, nextTextVal:\"nestText\"}}");
    }

    @Test
    public void testParamAnnotAttachmentsViaBir() {
        BCompileUtil.compileAndCacheBala("test-src/bala/test_projects/test_annotation_project");
        BCompileUtil.compileAndCacheBala("test-src/bala/test_projects/test_annotation_usage_project");
        CompileResult result = BCompileUtil.compile(
                "test-src/bala/test_bala/annotations/param_annot_attachments_bala_test.bal");

        BLangPackage bLangPackage = (BLangPackage) result.getAST();
        Map<Name, Scope.ScopeEntry> importedModuleEntries = bLangPackage.getImports().get(0).symbol.scope.entries;

        List<? extends AnnotationAttachmentSymbol> annotationAttachmentSymbols =
                ((BInvokableSymbol) importedModuleEntries.get(
                        Names.fromString("func")).symbol).params.get(0).getAnnotations();
        Assert.assertEquals(annotationAttachmentSymbols.size(), 1);
        BAnnotationAttachmentSymbol attachmentSymbol = (BAnnotationAttachmentSymbol) annotationAttachmentSymbols.get(0);
        PackageID pkgID = attachmentSymbol.annotPkgID;
        Assert.assertEquals(pkgID.orgName.value, "annots");
        Assert.assertEquals(pkgID.pkgName.value, "usage");
        Assert.assertEquals(pkgID.version.value, "0.2.0");
        Assert.assertEquals(attachmentSymbol.annotTag.value, "Allow");
        Assert.assertTrue(attachmentSymbol.isConstAnnotation());
        BAnnotationAttachmentSymbol.BConstAnnotationAttachmentSymbol constAttachmentSymbol =
                (BAnnotationAttachmentSymbol.BConstAnnotationAttachmentSymbol) attachmentSymbol;
        Assert.assertEquals(constAttachmentSymbol.attachmentValueSymbol.type.tag, TypeTags.BOOLEAN);
        Assert.assertEquals(constAttachmentSymbol.attachmentValueSymbol.value.value, Boolean.TRUE);

        BInvokableSymbol otherFunc = (BInvokableSymbol) importedModuleEntries.get(
                Names.fromString("otherFunc")).symbol;
        List<BVarSymbol> params = otherFunc.params;

        annotationAttachmentSymbols = params.get(0).getAnnotations();
        Assert.assertEquals(annotationAttachmentSymbols.size(), 0);

        annotationAttachmentSymbols = params.get(1).getAnnotations();
        Assert.assertEquals(annotationAttachmentSymbols.size(), 2);

        attachmentSymbol = (BAnnotationAttachmentSymbol) annotationAttachmentSymbols.get(0);
        pkgID = attachmentSymbol.annotPkgID;
        Assert.assertEquals(pkgID.orgName.value, "annots");
        Assert.assertEquals(pkgID.pkgName.value, "defn");
        Assert.assertEquals(pkgID.version.value, "0.0.1");
        Assert.assertEquals(attachmentSymbol.annotTag.value, "Annot");
        Assert.assertTrue(attachmentSymbol.isConstAnnotation());
        Object value = ((BAnnotationAttachmentSymbol.BConstAnnotationAttachmentSymbol) attachmentSymbol)
                .attachmentValueSymbol.value.value;
        Assert.assertTrue(value instanceof Map);
        Map<String, BLangConstantValue> mapValue = (Map<String, BLangConstantValue>) value;
        Assert.assertEquals(mapValue.size(), 1);
        Assert.assertEquals(mapValue.get("i").value, 456L);

        attachmentSymbol = (BAnnotationAttachmentSymbol) annotationAttachmentSymbols.get(1);
        pkgID = attachmentSymbol.annotPkgID;
        Assert.assertEquals(pkgID.orgName.value, "annots");
        Assert.assertEquals(pkgID.pkgName.value, "defn");
        Assert.assertEquals(pkgID.version.value, "0.0.1");
        Assert.assertEquals(attachmentSymbol.annotTag.value, "NonConstAnnot");
        Assert.assertFalse(attachmentSymbol.isConstAnnotation());

        annotationAttachmentSymbols = otherFunc.restParam.getAnnotations();
        Assert.assertEquals(annotationAttachmentSymbols.size(), 1);
        attachmentSymbol = (BAnnotationAttachmentSymbol) annotationAttachmentSymbols.get(0);
        pkgID = attachmentSymbol.annotPkgID;
        Assert.assertEquals(pkgID.orgName.value, "annots");
        Assert.assertEquals(pkgID.pkgName.value, "usage");
        Assert.assertEquals(pkgID.version.value, "0.2.0");
        Assert.assertEquals(attachmentSymbol.annotTag.value, "Allow");
        Assert.assertTrue(attachmentSymbol.isConstAnnotation());
        constAttachmentSymbol = (BAnnotationAttachmentSymbol.BConstAnnotationAttachmentSymbol) attachmentSymbol;
        Assert.assertEquals(constAttachmentSymbol.attachmentValueSymbol.type.tag, TypeTags.BOOLEAN);
        Assert.assertEquals(constAttachmentSymbol.attachmentValueSymbol.value.value, Boolean.TRUE);

        BInvokableSymbol anotherFunc = (BInvokableSymbol) importedModuleEntries.get(
                Names.fromString("anotherFunc")).symbol;
        params = anotherFunc.params;
        annotationAttachmentSymbols = params.get(0).getAnnotations();
        Assert.assertEquals(annotationAttachmentSymbols.size(), 1);
        attachmentSymbol = (BAnnotationAttachmentSymbol) annotationAttachmentSymbols.get(0);
        pkgID = attachmentSymbol.annotPkgID;
        Assert.assertEquals(pkgID.orgName.value, "annots");
        Assert.assertEquals(pkgID.pkgName.value, "usage");
        Assert.assertEquals(pkgID.version.value, "0.2.0");
        Assert.assertEquals(attachmentSymbol.annotTag.value, "NonConstAllow");
        Assert.assertFalse(attachmentSymbol.isConstAnnotation());

        BClassSymbol testListener =
                (BClassSymbol) importedModuleEntries.get(Names.fromString("TestListener")).symbol;

        params = testListener.initializerFunc.symbol.params;
        annotationAttachmentSymbols = params.get(0).getAnnotations();
        Assert.assertEquals(annotationAttachmentSymbols.size(), 0);
        annotationAttachmentSymbols = params.get(1).getAnnotations();
        Assert.assertEquals(annotationAttachmentSymbols.size(), 2);

        for (AnnotationAttachmentSymbol annotationAttachmentSymbol : annotationAttachmentSymbols) {
            attachmentSymbol = (BAnnotationAttachmentSymbol) annotationAttachmentSymbol;
            pkgID = attachmentSymbol.annotPkgID;
            Assert.assertEquals(pkgID.orgName.value, "annots");

            Assert.assertTrue(attachmentSymbol.isConstAnnotation());
            constAttachmentSymbol = (BAnnotationAttachmentSymbol.BConstAnnotationAttachmentSymbol) attachmentSymbol;
            Assert.assertEquals(constAttachmentSymbol.attachmentValueSymbol.type.tag, TypeTags.BOOLEAN);
            Assert.assertEquals(constAttachmentSymbol.attachmentValueSymbol.value.value, Boolean.TRUE);

            String pkgName = pkgID.pkgName.value;

            if ("defn".equals(pkgName)) {
                Assert.assertEquals(pkgID.version.value, "0.0.1");
                Assert.assertEquals(attachmentSymbol.annotTag.value, "Expose");
                continue;
            }

            Assert.assertEquals(pkgName, "usage");
            Assert.assertEquals(pkgID.version.value, "0.2.0");
            Assert.assertEquals(attachmentSymbol.annotTag.value, "Allow");
        }

        BAttachedFunction attachMethod = null;
        BAttachedFunction detachMethod = null;

        for (BAttachedFunction attachedFunc : testListener.attachedFuncs) {
            String funcName = attachedFunc.funcName.value;

            if ("attach".equals(funcName)) {
                attachMethod = attachedFunc;
                continue;
            }

            if ("detach".equals(funcName)) {
                detachMethod = attachedFunc;
            }
        }

        params = attachMethod.symbol.params;
        annotationAttachmentSymbols = params.get(1).getAnnotations();
        Assert.assertEquals(annotationAttachmentSymbols.size(), 0);
        annotationAttachmentSymbols = params.get(0).getAnnotations();
        Assert.assertEquals(annotationAttachmentSymbols.size(), 1);
        attachmentSymbol = (BAnnotationAttachmentSymbol) annotationAttachmentSymbols.get(0);
        pkgID = attachmentSymbol.annotPkgID;
        Assert.assertEquals(pkgID.orgName.value, "annots");
        Assert.assertEquals(pkgID.pkgName.value, "defn");
        Assert.assertEquals(pkgID.version.value, "0.0.1");
        Assert.assertEquals(attachmentSymbol.annotTag.value, "Annot");
        constAttachmentSymbol = (BAnnotationAttachmentSymbol.BConstAnnotationAttachmentSymbol) attachmentSymbol;
        value = constAttachmentSymbol.attachmentValueSymbol.value.value;
        Assert.assertTrue(value instanceof Map);
        mapValue = (Map<String, BLangConstantValue>) value;
        Assert.assertEquals(mapValue.size(), 1);
        Assert.assertEquals(mapValue.get("i").value, 1L);
        BType type = constAttachmentSymbol.attachmentValueSymbol.type;
        Assert.assertEquals(type.tag, TypeTags.INTERSECTION);
        BIntersectionType intersectionType = (BIntersectionType) type;
        BType effectiveType = intersectionType.effectiveType;
        Assert.assertEquals(effectiveType.tag, TypeTags.RECORD);

        params = detachMethod.symbol.params;
        annotationAttachmentSymbols = params.get(0).getAnnotations();
        Assert.assertEquals(annotationAttachmentSymbols.size(), 2);

        List<Long> members = Lists.of(2L, 3L);

        for (AnnotationAttachmentSymbol annotationAttachmentSymbol : annotationAttachmentSymbols) {
            attachmentSymbol = (BAnnotationAttachmentSymbol) annotationAttachmentSymbol;
            pkgID = attachmentSymbol.annotPkgID;
            Assert.assertEquals(pkgID.orgName.value, "annots");
            Assert.assertEquals(pkgID.pkgName.value, "defn");
            Assert.assertEquals(pkgID.version.value, "0.0.1");
            Assert.assertEquals(attachmentSymbol.annotTag.value, "Annots");
            constAttachmentSymbol = (BAnnotationAttachmentSymbol.BConstAnnotationAttachmentSymbol) attachmentSymbol;
            value = constAttachmentSymbol.attachmentValueSymbol.value.value;
            Assert.assertTrue(value instanceof Map);
            mapValue = (Map<String, BLangConstantValue>) value;
            Assert.assertEquals(mapValue.size(), 1);
            Assert.assertTrue(members.remove(mapValue.get("i").value));
            type = constAttachmentSymbol.attachmentValueSymbol.type;
            Assert.assertEquals(type.tag, TypeTags.INTERSECTION);
            type = ((BIntersectionType) type).effectiveType;
            Assert.assertEquals(type.tag, TypeTags.RECORD);
        }
    }

    @AfterClass
    public void tearDown() {
        result = null;
    }
}
