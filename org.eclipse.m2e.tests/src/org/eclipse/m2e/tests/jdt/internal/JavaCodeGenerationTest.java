/*******************************************************************************
 * Copyright (c) 2010 Sonatype, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *      Sonatype, Inc. - initial API and implementation
 *******************************************************************************/

package org.eclipse.m2e.tests.jdt.internal;

import java.util.Arrays;
import java.util.List;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IncrementalProjectBuilder;
import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.JavaCore;

import org.codehaus.plexus.util.FileUtils;

import org.eclipse.m2e.core.project.IMavenProjectFacade;
import org.eclipse.m2e.core.project.configurator.AbstractProjectConfigurator;
import org.eclipse.m2e.core.project.configurator.ILifecycleMapping;
import org.eclipse.m2e.jdt.internal.JavaProjectConfigurator;
import org.eclipse.m2e.jdt.internal.SourcesGenerationProjectConfigurator;
import org.eclipse.m2e.tests.common.AbstractLifecycleMappingTest;
import org.eclipse.m2e.tests.common.WorkspaceHelpers;


public class JavaCodeGenerationTest extends AbstractLifecycleMappingTest {

  public void testMavenPluginEmbeddedMetadata() throws Exception {
    IMavenProjectFacade facade = importMavenProject("projects/codegeneration/basic", "pom.xml");
    assertNotNull("Expected not null MavenProjectFacade", facade);
    IProject project = facade.getProject();
    WorkspaceHelpers.assertNoErrors(project);

    ILifecycleMapping lifecycleMapping = projectConfigurationManager.getLifecycleMapping(facade);
    assertNotNull(lifecycleMapping);

    List<AbstractProjectConfigurator> configurators = lifecycleMapping.getProjectConfigurators(facade, monitor);
    assertEquals(2, configurators.size());
    assertTrue(configurators.get(0) instanceof SourcesGenerationProjectConfigurator);
    assertTrue(configurators.get(1) instanceof JavaProjectConfigurator);

    // assert static configuration
    IJavaProject javaProject = JavaCore.create(project);
    IClasspathEntry[] cp = javaProject.getRawClasspath();

    assertEquals(3, cp.length);
    assertTrue(cp[0].getPath().toPortableString().startsWith("org.eclipse.jdt.launching.JRE_CONTAINER"));
    assertEquals("org.eclipse.m2e.MAVEN2_CLASSPATH_CONTAINER", cp[1].getPath().toPortableString());
    assertEquals("/basic/target/generated-sources/test-resources", cp[2].getPath().toPortableString());

    // sanity check
    IFile file = project.getFile("target/generated-sources/test-resources/test.txt");
    assertFalse(file.isAccessible());

    // first build is expected to create the generated sources
    project.build(IncrementalProjectBuilder.FULL_BUILD, monitor);
    file = project.getFile("target/generated-sources/test-resources/test.txt"); // TODO do I actually need to re-get the file?
    assertTrue(file.isAccessible());
    assertTrue(file.isSynchronized(IResource.DEPTH_ZERO));
    assertEquals(Arrays.asList("test-text"), FileUtils.loadFile(file.getLocation().toFile()));

    // incremental build without a change does nothing
    project.build(IncrementalProjectBuilder.INCREMENTAL_BUILD, monitor);
    file = project.getFile("target/generated-sources/test-resources/test.txt"); // TODO do I actually need to re-get the file?
    assertTrue(file.isAccessible());
    assertTrue(file.isSynchronized(IResource.DEPTH_ZERO));
    assertEquals(Arrays.asList("test-text"), FileUtils.loadFile(file.getLocation().toFile()));

    // incremental build with change regenerates the sources (appends the output in this case)
    project.getFile("src/main/test-resources/test.txt").touch(monitor);
    project.build(IncrementalProjectBuilder.INCREMENTAL_BUILD, monitor);
    file = project.getFile("target/generated-sources/test-resources/test.txt"); // TODO do I actually need to re-get the file?
    assertTrue(file.isAccessible());
    assertTrue(file.isSynchronized(IResource.DEPTH_ZERO));
    assertEquals(Arrays.asList("test-texttest-text"), FileUtils.loadFile(file.getLocation().toFile()));

    // clean build removes the output
    project.build(IncrementalProjectBuilder.CLEAN_BUILD, monitor);
    assertFalse(file.isAccessible());

  }

}
