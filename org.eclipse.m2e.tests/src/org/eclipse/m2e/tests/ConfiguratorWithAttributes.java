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

package org.eclipse.m2e.tests;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;

import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.MojoExecution;

import org.eclipse.m2e.core.project.configurator.AbstractProjectConfigurator2;
import org.eclipse.m2e.core.project.configurator.ProjectConfigurationRequest;


public class ConfiguratorWithAttributes extends AbstractProjectConfigurator2 {

  public void configure(ProjectConfigurationRequest request, IProgressMonitor monitor) {
  }

  public <T> T getConfiguratorParameterValue(String parameter, Class<T> asType, MavenSession session,
      MojoExecution mojoExecution) throws CoreException {
    return super.getConfiguratorParameterValue(parameter, asType, session, mojoExecution);
  }
}
