/*
 * Copyright 2005 JBoss Inc
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.drools.modelcompiler;

import java.io.File;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import org.drools.compiler.kie.builder.impl.InternalKieModule;
import org.drools.compiler.kie.builder.impl.KieBuilderImpl;
import org.drools.modelcompiler.builder.CanonicalModelKieProject;
import org.drools.modelcompiler.util.TestFileUtils;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;
import org.kie.api.KieServices;
import org.kie.api.builder.KieBuilder;
import org.kie.api.builder.KieFileSystem;
import org.kie.api.builder.KieModule;
import org.kie.api.builder.Message;
import org.kie.api.builder.ReleaseId;
import org.kie.api.builder.model.KieModuleModel;
import org.kie.api.runtime.ClassObjectFilter;
import org.kie.api.runtime.KieContainer;
import org.kie.api.runtime.KieSession;

import static org.junit.Assert.fail;

@RunWith(Parameterized.class)
public abstract class BaseModelTest {
    public static enum RUN_TYPE {
        USE_CANONICAL_MODEL,
        STANDARD_FROM_DRL;
    }

    @Parameters(name = "{0}")
    public static Object[] params() {
        return new Object[]{
                BaseModelTest.RUN_TYPE.STANDARD_FROM_DRL,
                BaseModelTest.RUN_TYPE.USE_CANONICAL_MODEL
        };
    }

    protected final CompilerTest.RUN_TYPE testRunType;

    public BaseModelTest( CompilerTest.RUN_TYPE testRunType ) {
        this.testRunType = testRunType;
    }

    protected KieSession getKieSession( String... rules ) {
        return getKieSession(null, rules);
    }

    protected KieSession getKieSession(KieModuleModel model, String... stringRules) {
        return getKieContainer( model, stringRules ).newKieSession();
    }

    protected KieContainer getKieContainer( KieModuleModel model, String... stringRules ) {
        return getKieContainer( model, toKieFiles( stringRules ) );
    }

    protected KieContainer getKieContainer( KieModuleModel model, KieFile... stringRules ) {
        KieServices ks = KieServices.get();
        ReleaseId releaseId = ks.newReleaseId( "org.kie", "kjar-test-" + UUID.randomUUID(), "1.0" );

        KieBuilder kieBuilder = createKieBuilder( ks, model, releaseId, stringRules );
        return getKieContainer( ks, model, releaseId, kieBuilder );
    }

    protected KieContainer getKieContainer( KieServices ks, KieModuleModel model, ReleaseId releaseId, KieBuilder kieBuilder ) {
        if ( testRunType == RUN_TYPE.USE_CANONICAL_MODEL ) {
            addKieModuleFromCanonicalModel( ks, model, releaseId, (InternalKieModule) kieBuilder.getKieModule() );
        }
        return ks.newKieContainer( releaseId );
    }

    protected void addKieModuleFromCanonicalModel( KieServices ks, KieModuleModel model, ReleaseId releaseId, InternalKieModule kieModule ) {
        File kjarFile = TestFileUtils.bytesToTempKJARFile( releaseId, kieModule.getBytes(), ".jar" );
        KieModule zipKieModule = new CanonicalKieModule( releaseId, model != null ? model : getDefaultKieModuleModel( ks ), kjarFile );
        ks.getRepository().addKieModule( zipKieModule );
    }

    protected KieBuilder createKieBuilder( String... stringRules ) {
        KieServices ks = KieServices.get();
        ReleaseId releaseId = ks.newReleaseId( "org.kie", "kjar-test-" + UUID.randomUUID(), "1.0" );
        return createKieBuilder( ks, null, releaseId, false, toKieFiles( stringRules ) );
    }

    protected KieBuilder createKieBuilder( KieServices ks, KieModuleModel model, ReleaseId releaseId, KieFile... stringRules ) {
        return createKieBuilder( ks, model, releaseId, true, stringRules );
    }

    protected KieBuilder createKieBuilder( KieServices ks, KieModuleModel model, ReleaseId releaseId, boolean failIfBuildError, KieFile... stringRules ) {
        ks.getRepository().removeKieModule( releaseId );

        KieFileSystem kfs = ks.newKieFileSystem();
        if ( model != null ) {
            kfs.writeKModuleXML( model.toXML() );
        }
        kfs.writePomXML( KJARUtils.getPom( releaseId ) );
        for (int i = 0; i < stringRules.length; i++) {
            kfs.write( stringRules[i].path, stringRules[i].content );
        }

        KieBuilder kieBuilder = ( testRunType == RUN_TYPE.USE_CANONICAL_MODEL ) ?
                ( (KieBuilderImpl ) ks.newKieBuilder( kfs ) ).buildAll( CanonicalModelKieProject::new ) :
                ks.newKieBuilder( kfs ).buildAll();

        if ( failIfBuildError ) {
            List<Message> messages = kieBuilder.getResults().getMessages();
            if ( !messages.isEmpty() ) {
                fail( messages.toString() );
            }
        }

        return kieBuilder;
    }

    protected KieModuleModel getDefaultKieModuleModel( KieServices ks ) {
        KieModuleModel kproj = ks.newKieModuleModel();
        kproj.newKieBaseModel( "kbase" ).setDefault( true ).newKieSessionModel( "ksession" ).setDefault( true );
        return kproj;
    }

    public static <T> List<T> getObjectsIntoList(KieSession ksession, Class<T> clazz) {
        return (List<T>) ksession.getObjects(new ClassObjectFilter(clazz)).stream().collect(Collectors.toList());
    }

    protected void createAndDeployJar( KieServices ks, ReleaseId releaseId, String... drls ) {
        createAndDeployJar( ks, null, releaseId, drls );
    }

    protected void createAndDeployJar( KieServices ks, ReleaseId releaseId, KieFile... ruleFiles ) {
        createAndDeployJar( ks, null, releaseId, ruleFiles );
    }

    protected void createAndDeployJar( KieServices ks, KieModuleModel model, ReleaseId releaseId, String... drls ) {
        createAndDeployJar( ks, model, releaseId, toKieFiles( drls ) );
    }

    protected void createAndDeployJar( KieServices ks, KieModuleModel model, ReleaseId releaseId, KieFile... ruleFiles ) {
        KieBuilder kieBuilder = createKieBuilder( ks, model, releaseId, ruleFiles );
        InternalKieModule kieModule = (InternalKieModule) kieBuilder.getKieModule();

        // Deploy jar into the repository
        if ( testRunType == RUN_TYPE.STANDARD_FROM_DRL ) {
            ks.getRepository().addKieModule( ks.getResources().newByteArrayResource( kieModule.getBytes() ) );
        } else if ( testRunType == RUN_TYPE.USE_CANONICAL_MODEL ) {
            addKieModuleFromCanonicalModel( ks, model, releaseId, kieModule );
        }
    }

    public static class KieFile {

        public final String path;
        public final String content;

        public KieFile( int index, String content ) {
            this( String.format("src/main/resources/r%d.drl", index), content );
        }

        public KieFile( String path, String content ) {
            this.path = path;
            this.content = content;
        }
    }

    public KieFile[] toKieFiles(String[] stringRules) {
        KieFile[] kieFiles = new KieFile[stringRules.length];
        for (int i = 0; i < stringRules.length; i++) {
            kieFiles[i] = new KieFile( i, stringRules[i] );
        }
        return kieFiles;
    }
}
