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

package org.eclipse.m2e.tests.lifecycle;

import java.io.File;
import java.util.Arrays;
import java.util.List;

import org.eclipse.core.resources.IProject;

import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.MojoExecution;

import org.eclipse.m2e.core.MavenPlugin;
import org.eclipse.m2e.core.embedder.IMaven;
import org.eclipse.m2e.core.project.IMavenProjectFacade;
import org.eclipse.m2e.core.project.configurator.AbstractProjectConfigurator;
import org.eclipse.m2e.core.project.configurator.ILifecycleMapping;
import org.eclipse.m2e.core.project.configurator.MojoExecutionKey;
import org.eclipse.m2e.tests.ConfiguratorWithAttributes;
import org.eclipse.m2e.tests.common.AbstractLifecycleMappingTest;
import org.eclipse.m2e.tests.common.WorkspaceHelpers;


public class ConfiguratorConfigurationInjectionTest extends AbstractLifecycleMappingTest {

  public void testBasic() throws Exception {
    IMavenProjectFacade facade = importMavenProject("projects/lifecyclemapping/configuration", "pom.xml");
    assertNotNull("Expected not null MavenProjectFacade", facade);
    IProject project = facade.getProject();
    WorkspaceHelpers.assertNoErrors(project);

    ILifecycleMapping lifecycleMapping = projectConfigurationManager.getLifecycleMapping(facade);
    assertNotNull(lifecycleMapping);

    List<AbstractProjectConfigurator> configurators = lifecycleMapping.getProjectConfigurators(facade, monitor);
    assertEquals(1, configurators.size());
    ConfiguratorWithAttributes configurator = (ConfiguratorWithAttributes) configurators.get(0);

    IMaven maven = MavenPlugin.getMaven();

    MavenSession session = maven.createSession(maven.createExecutionRequest(monitor), facade.getMavenProject());

    MojoExecution execution = facade.getMojoExecution(new MojoExecutionKey("org.eclipse.m2e.test.lifecyclemapping",
        "test-lifecyclemapping-plugin", "1.0.0", "test-goal-with-parameters", "generate-sources", "test"), monitor);

    assertEquals("string-value",
        configurator.getConfiguratorParameterValue("stringAttribute", String.class, session, execution));

    assertEquals("string-mojo-parameter",
        configurator.getConfiguratorParameterValue("stringMojoParameter", String.class, session, execution));
    assertEquals(facade.getMavenProject().getBasedir(),
        configurator.getConfiguratorParameterValue("fileMojoParameter", File.class, session, execution));
    assertEquals(new File[] {facade.getMavenProject().getBasedir()},
        configurator.getConfiguratorParameterValue("fileArrayMojoParameter", File[].class, session, execution));
  }

  private <T> void assertEquals(T[] a, T[] b) {
    assertEquals(Arrays.asList(a), Arrays.asList(a));
  }
}
