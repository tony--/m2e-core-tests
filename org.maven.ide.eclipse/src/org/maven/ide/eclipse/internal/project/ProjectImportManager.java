/*******************************************************************************
 * Copyright (c) 2008 Sonatype, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/

package org.maven.ide.eclipse.internal.project;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Set;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IProjectDescription;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IWorkspace;
import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.OperationCanceledException;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.QualifiedName;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.ISchedulingRule;
import org.eclipse.jdt.core.IClasspathContainer;
import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.JavaCore;

import org.apache.maven.archetype.ArchetypeGenerationRequest;
import org.apache.maven.archetype.ArchetypeGenerationResult;
import org.apache.maven.archetype.catalog.Archetype;
import org.apache.maven.embedder.MavenEmbedder;
import org.apache.maven.embedder.MavenEmbedderException;
import org.apache.maven.model.Model;

import org.maven.ide.eclipse.MavenConsole;
import org.maven.ide.eclipse.MavenPlugin;
import org.maven.ide.eclipse.embedder.EmbedderFactory;
import org.maven.ide.eclipse.embedder.MavenEmbedderManager;
import org.maven.ide.eclipse.embedder.MavenModelManager;
import org.maven.ide.eclipse.embedder.MavenRuntimeManager;
import org.maven.ide.eclipse.index.IndexManager;
import org.maven.ide.eclipse.internal.embedder.TransferListenerAdapter;
import org.maven.ide.eclipse.project.BuildPathManager;
import org.maven.ide.eclipse.project.IProjectImportManager;
import org.maven.ide.eclipse.project.LocalProjectScanner;
import org.maven.ide.eclipse.project.MavenProjectFacade;
import org.maven.ide.eclipse.project.MavenProjectInfo;
import org.maven.ide.eclipse.project.ProjectImportConfiguration;
import org.maven.ide.eclipse.project.ResolverConfiguration;
import org.maven.ide.eclipse.util.Util;

/**
 * MavenProjectImporter
 *
 * @author igor
 */
public class ProjectImportManager implements IProjectImportManager {
  
  static final QualifiedName QNAME = new QualifiedName(MavenPlugin.PLUGIN_ID, "ProjectImportManager");

  final MavenModelManager modelManager;

  final MavenConsole console;

  final MavenRuntimeManager runtimeManager;

  final MavenProjectManagerImpl projectManager;

  final IndexManager indexManager;

  final MavenEmbedderManager embedderManager;

  final JavaProjectConfigurator javaConfigurator;
  
  public ProjectImportManager(MavenModelManager modelManager, MavenConsole console, 
        MavenRuntimeManager runtimeManager, MavenProjectManagerImpl projectManager, 
        IndexManager indexManager, MavenEmbedderManager embedderManager) 
  {
    this.modelManager = modelManager;
    this.console = console;
    this.runtimeManager = runtimeManager;
    this.projectManager = projectManager;
    this.indexManager = indexManager;
    this.embedderManager = embedderManager;

    this.javaConfigurator = new JavaProjectConfigurator(runtimeManager, console, projectManager);
  }

  public void importProjects(Collection projectInfos, ProjectImportConfiguration configuration, IProgressMonitor monitor) throws CoreException {
    try {
      IMavenProjectConfigurator[] configurators = new IMavenProjectConfigurator[] {
          javaConfigurator
      };

      MavenEmbedder embedder = embedderManager.createEmbedder(EmbedderFactory.createWorkspaceCustomizer());
      try {
        MavenUpdateRequest updateRequest = new MavenUpdateRequest(false, false);

        IdentityHashMap/*<MavenProjectInfo, IProject>*/ projects = new IdentityHashMap/*<MavenProjectInfo, IProject>*/();

        // first, create all projects with basic configuration
        for(Iterator it = projectInfos.iterator(); it.hasNext();) {
          if(monitor.isCanceled()) {
            throw new OperationCanceledException();
          }

          MavenProjectInfo projectInfo = (MavenProjectInfo) it.next();

          IProject project = create(projectInfo, configuration, monitor);

          projects.put(projectInfo, project);
          updateRequest.addPomFile(project);
        }

        // next, resolve maven dependencies for all projects
        while(!updateRequest.isEmpty()) {
          IFile pom = updateRequest.pop();
          monitor.subTask(pom.getFullPath().toString());

          projectManager.refresh(embedder, pom, updateRequest, monitor);
          monitor.worked(1);
        }

        // and finally, perform detailed project configuration
        for(Iterator it = projectInfos.iterator(); it.hasNext();) {
          MavenProjectInfo projectInfo = (MavenProjectInfo) it.next();
          IProject project = (IProject) projects.get(projectInfo);
          MavenProjectFacade facade = projectManager.create(project, monitor);

          if (facade != null) {
            for (int i = 0; i < configurators.length; i++) {
              if(monitor.isCanceled()) {
                throw new OperationCanceledException();
              }
              configurators[i].configure(embedder, facade, monitor);
            }
          }
        }
      } finally {
        embedder.stop();
      }

      projectManager.notifyProjectChangeListeners(monitor);

    } catch(MavenEmbedderException ex) {
      throw new CoreException(new Status(IStatus.ERROR, MavenPlugin.PLUGIN_ID, ex.getMessage(), ex));
    }
  }

  public void configureProject(IProject project, ResolverConfiguration configuration, IProgressMonitor monitor)
      throws CoreException {
    enableMavenNature(project, configuration, monitor);
    updateSourceFolders(project, configuration, runtimeManager.getGoalOnImport(), monitor);
  }

  public void updateSourceFolders(IProject project, ResolverConfiguration configuration, String goalToExecute, IProgressMonitor monitor) throws CoreException {
    try {
      MavenEmbedder embedder = embedderManager.createEmbedder(EmbedderFactory.createExecutionCustomizer());
      try {
        javaConfigurator.updateSourceFolders(embedder, project, configuration, runtimeManager.getGoalOnUpdate(), monitor);
      } finally {
        embedder.stop();
      }
    } catch (MavenEmbedderException ex) {
      throw new CoreException(new Status(IStatus.ERROR, MavenPlugin.PLUGIN_ID, ex.getMessage(), ex));
    }
  }

  public void enableMavenNature(IProject project, ResolverConfiguration configuration, IProgressMonitor monitor)
      throws CoreException {
    monitor.subTask("Enable Maven nature");

    ArrayList newNatures = new ArrayList();
    newNatures.add(JavaCore.NATURE_ID);
    newNatures.add(MavenPlugin.NATURE_ID);

    IProjectDescription description = project.getDescription();
    String[] natures = description.getNatureIds();
    for(int i = 0; i < natures.length; ++i) {
      String id = natures[i];
      if(!MavenPlugin.NATURE_ID.equals(id) && !JavaCore.NATURE_ID.equals(natures[i])) {
        newNatures.add(natures[i]);
      }
    }
    description.setNatureIds((String[]) newNatures.toArray(new String[newNatures.size()]));
    project.setDescription(description, monitor);
    
    projectManager.setResolverConfiguration(project, configuration);

    IJavaProject javaProject = JavaCore.create(project);
    if(javaProject != null) {
      Set containerEntrySet = new LinkedHashSet();
      IClasspathContainer container = BuildPathManager.getMaven2ClasspathContainer(javaProject);
      if(container != null) {
        IClasspathEntry[] entries = container.getClasspathEntries();
        for(int i = 0; i < entries.length; i++ ) {
          containerEntrySet.add(entries[i].getPath().toString());
        }
      }

      // remove classpath container from JavaProject
      IClasspathEntry[] entries = javaProject.getRawClasspath();
      ArrayList newEntries = new ArrayList();
      for(int i = 0; i < entries.length; i++ ) {
        IClasspathEntry entry = entries[i];
        if(!BuildPathManager.isMaven2ClasspathContainer(entry.getPath()) && !containerEntrySet.contains(entry.getPath().toString())) {
          newEntries.add(entry);
        }
      }

      newEntries.add(createContainerEntry(configuration));

      javaProject.setRawClasspath((IClasspathEntry[]) newEntries.toArray(new IClasspathEntry[newEntries.size()]),
          monitor);
    }
  }

  public void disableMavenNature(IProject project, IProgressMonitor monitor) throws CoreException {
    monitor.subTask("Disable Maven nature");

    project.deleteMarkers(MavenPlugin.MARKER_ID, true, IResource.DEPTH_INFINITE);

    IProjectDescription description = project.getDescription();
    String[] natures = description.getNatureIds();
    ArrayList newNatures = new ArrayList();
    for(int i = 0; i < natures.length; ++i) {
      if(!MavenPlugin.NATURE_ID.equals(natures[i])) {
        newNatures.add(natures[i]);
      }
    }
    description.setNatureIds((String[]) newNatures.toArray(new String[newNatures.size()]));
    project.setDescription(description, null);

    IJavaProject javaProject = JavaCore.create(project);
    if(javaProject != null) {
      // remove classpatch container from JavaProject
      IClasspathEntry[] entries = javaProject.getRawClasspath();
      ArrayList newEntries = new ArrayList();
      for(int i = 0; i < entries.length; i++ ) {
        if(!BuildPathManager.isMaven2ClasspathContainer(entries[i].getPath())) {
          newEntries.add(entries[i]);
        }
      }
      javaProject.setRawClasspath((IClasspathEntry[]) newEntries.toArray(new IClasspathEntry[newEntries.size()]),
          null);
    }

  }

  private static IClasspathEntry createContainerEntry(ResolverConfiguration configuration) {
    IPath newPath = new Path(MavenPlugin.CONTAINER_ID);

    return JavaCore.newContainerEntry(newPath);
  }
  // project creation

  /**
   * Creates simple Maven project
   * <p>
   * The following steps are executed in the given order:
   * <ul>
   * <li>Creates the workspace project</li>
   * <li>Creates the required folders</li>
   * <li>Creates the POM</li>
   * <li>Configures project</li>
   * </ul>
   * </p>
   */
  // XXX should use Maven plugin configurations instead of manually specifying folders
  public void createSimpleProject(IProject project, IPath location, Model model, String[] directories,
      ResolverConfiguration resolverConfiguration, IProgressMonitor monitor) throws CoreException {
    String projectName = project.getName();
    monitor.beginTask("Creating project " + projectName, 4);

    monitor.subTask("Creating workspace project...");
    IProjectDescription description = ResourcesPlugin.getWorkspace().newProjectDescription(projectName);
    description.setLocation(location);
    project.create(description, monitor);
    project.open(monitor);
    monitor.worked(1);

    monitor.subTask("Creating the POM file...");
    IFile pomFile = project.getFile(MavenPlugin.POM_FILE_NAME);    
    modelManager.createMavenModel(pomFile, model);
    monitor.worked(1);

    monitor.subTask("Creating project folders...");
    for(int i = 0; i < directories.length; i++ ) {
      Util.createFolder(project.getFolder(directories[i]));
    }
    monitor.worked(1);

    monitor.subTask("Configuring project...");
    configureProject(project, resolverConfiguration, monitor);

    monitor.worked(1);
  }
  
  /**
   * Creates project structure using Archetype and then imports created project
   */
  public void createArchetypeProject(IProject project, IPath location, Archetype archetype, String groupId,
      String artifactId, String version, String javaPackage, ProjectImportConfiguration configuration, IProgressMonitor monitor) throws CoreException {
    monitor.beginTask("Creating project " + project.getName(), 2);
  
    monitor.subTask("Executing Archetype " + archetype.getGroupId() + ":" + archetype.getArtifactId());
    if(location == null) {
      // if the project should be created in the workspace, figure out the path
      location = ResourcesPlugin.getWorkspace().getRoot().getLocation();
    }

    ArchetypeGenerationRequest request = new ArchetypeGenerationRequest() //
        .setTransferListener(new TransferListenerAdapter(new NullProgressMonitor(), console, indexManager)) //
        .setArchetypeGroupId(archetype.getGroupId()) //
        .setArchetypeArtifactId(archetype.getArtifactId()) //
        .setArchetypeVersion(archetype.getVersion()) //
        .setArchetypeRepository(archetype.getRepository()) //
        .setGroupId(groupId) //
        .setArtifactId(artifactId) //
        .setVersion(version) //
        .setPackage(javaPackage) // the model does not have a package field
        .setLocalRepository(embedderManager.getWorkspaceEmbedder().getLocalRepository()) //
        .setOutputDirectory(location.toPortableString());
    
    ArchetypeGenerationResult result = embedderManager.getArchetyper().generateProjectFromArchetype(request);
    Exception cause = result.getCause();
    if(cause != null) {
      String msg = "Unable to create project from archetype " + archetype.toString();
      MavenPlugin.log(msg, cause);
      throw new CoreException(new Status(IStatus.ERROR, MavenPlugin.PLUGIN_ID, 0, msg, cause));
    }
    monitor.worked(1);
    
    // XXX Archetyper don't allow to specify project folder
    String projectFolder = location.append(artifactId).toFile().getAbsolutePath();
    
    LocalProjectScanner scanner = new LocalProjectScanner(projectFolder, true);
    try {
      scanner.run(monitor);
    } catch (InterruptedException e) {
      throw new CoreException(Status.CANCEL_STATUS);
    }

    Set projectSet = collectProjects(scanner.getProjects(), configuration.getResolverConfiguration().shouldIncludeModules());
    
    importProjects(projectSet, configuration, monitor);

    monitor.worked(1);
  }

  /**
   * Flatten hierarchical projects
   *   
   * @param projects a collection of {@link MavenProjectInfo}
   * @param includeModules of true 
   * 
   * @return flattened collection of {@link MavenProjectInfo}
   */
  public Set collectProjects(Collection projects, boolean includeModules) {
    Set projectSet = collectProjects(projects);
    if(!includeModules) {
      return projectSet;
    }
    
    Set parentProjects = new HashSet();
    for(Iterator it = projectSet.iterator(); it.hasNext();) {
      MavenProjectInfo projectInfo = (MavenProjectInfo) it.next();
      MavenProjectInfo parent = projectInfo.getParent();
      if(parent==null || !projectSet.contains(parent)) {
        parentProjects.add(projectInfo);
      }
    }
    return parentProjects;
  }

  private Set collectProjects(Collection projects) {
    return new LinkedHashSet() {
      private static final long serialVersionUID = 1L;
      public Set collectProjects(Collection projects) {
        for(Iterator it = projects.iterator(); it.hasNext();) {
          MavenProjectInfo projectInfo = (MavenProjectInfo) it.next();
          add(projectInfo);
          collectProjects(projectInfo.getProjects());
        }
        return this;
      }
    }.collectProjects(projects);
  }

  public ISchedulingRule getRule() {
    return new SchedulingRule(false);
  }

  private IProject create(MavenProjectInfo projectInfo, ProjectImportConfiguration configuration, IProgressMonitor monitor) throws CoreException {
    File pomFile = projectInfo.getPomFile(); 
    Model model = projectInfo.getModel();
    if(model == null) {
      model = modelManager.readMavenModel(pomFile);
      projectInfo.setModel(model);
    }

    String projectName = configuration.getProjectName(model);

    monitor.subTask("Importing project " + projectName);
    
    IWorkspace workspace = ResourcesPlugin.getWorkspace();
    IWorkspaceRoot root = workspace.getRoot();
    IProject project = configuration.getProject(root, model);
    if(project.exists()) {
      console.logError("Project " + projectName + " already exists");
      return null;
    }

    File projectDir = pomFile.getParentFile();

    if(projectDir.equals(root.getLocation().toFile())) {
      console.logError("Can't create project " + projectName + " at Workspace folder");
      return null;
    }

    if(projectInfo.isNeedsRename()) {
      File newProject = new File(projectDir.getParent(), projectName);
      boolean renamed = projectDir.renameTo(newProject);
      if(!renamed) {
        throw new CoreException(new Status(IStatus.ERROR, MavenPlugin.PLUGIN_ID, -1, "Can't rename " + projectDir.getAbsolutePath(), null));
      }
      projectInfo.setPomFile(new File(newProject, MavenPlugin.POM_FILE_NAME));
      projectDir = newProject;
    }

    String projectParent = projectDir.getParentFile().getAbsolutePath();
    if(projectParent.equals(root.getLocation().toFile().getAbsolutePath())) {
      // rename dir in workspace to match expected project name
      if(!projectDir.equals(root.getLocation().append(project.getName()).toFile())) {
        File newProject = new File(projectDir.getParent(), projectName);
        boolean renamed = projectDir.renameTo(newProject);
        if(!renamed) {
          throw new CoreException(new Status(IStatus.ERROR, MavenPlugin.PLUGIN_ID, -1, "Can't rename " + projectDir.getAbsolutePath(), null));
        }
        projectInfo.setPomFile(new File(newProject, MavenPlugin.POM_FILE_NAME));
      }
      project.create(monitor);
    } else {
      IProjectDescription description = workspace.newProjectDescription(projectName);
      description.setLocation(new Path(projectDir.getAbsolutePath()));
      project.create(description, monitor);
    }

    if(!project.isOpen()) {
      project.open(monitor);
    }

    ResolverConfiguration resolverConfiguration = configuration.getResolverConfiguration();
    projectManager.setResolverConfiguration(project, resolverConfiguration);

    // XXX split maven and java nature configuration
    enableMavenNature(project, resolverConfiguration, monitor);

    return project;
  }

}
