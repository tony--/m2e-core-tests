/*******************************************************************************
 * Copyright (c) 2008-2010 Sonatype, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *      Sonatype, Inc. - initial API and implementation
 *******************************************************************************/

package org.eclipse.m2e.tests.internal.index;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.NullProgressMonitor;

import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.index.ArtifactInfo;
import org.apache.maven.settings.Settings;
import org.apache.maven.wagon.authentication.AuthenticationInfo;

import org.eclipse.m2e.core.MavenPlugin;
import org.eclipse.m2e.core.embedder.ArtifactKey;
import org.eclipse.m2e.core.embedder.IMaven;
import org.eclipse.m2e.core.embedder.IMavenConfiguration;
import org.eclipse.m2e.core.internal.index.IIndex;
import org.eclipse.m2e.core.internal.index.IndexedArtifact;
import org.eclipse.m2e.core.internal.index.IndexedArtifactFile;
import org.eclipse.m2e.core.internal.index.SourcedSearchExpression;
import org.eclipse.m2e.core.internal.index.UserInputSearchExpression;
import org.eclipse.m2e.core.internal.index.nexus.IndexedArtifactGroup;
import org.eclipse.m2e.core.internal.index.nexus.NexusIndex;
import org.eclipse.m2e.core.internal.index.nexus.NexusIndexManager;
import org.eclipse.m2e.core.internal.repository.RepositoryInfo;
import org.eclipse.m2e.core.internal.repository.RepositoryRegistry;
import org.eclipse.m2e.core.project.IMavenProjectFacade;
import org.eclipse.m2e.core.project.MavenProjectChangedEvent;
import org.eclipse.m2e.core.project.ResolverConfiguration;
import org.eclipse.m2e.core.repository.IRepository;
import org.eclipse.m2e.core.repository.IRepositoryRegistry;
import org.eclipse.m2e.tests.common.FileHelpers;
import org.eclipse.m2e.tests.common.HttpServer;


/**
 * @author dyocum
 */
public class NexusIndexManagerTest extends AbstractNexusIndexManagerTest {
  private static final String SETTINGS_NO_MIRROR = "src/org/eclipse/m2e/tests/internal/index/no_mirror_settings.xml";

  private static final String SETTINGS_PUBLIC_JBOSS_NOTMIRRORED = "src/org/eclipse/m2e/tests/internal/index/public_nonmirrored_repo_settings.xml";

  private static final String SETTINGS_ECLIPSE_REPO = "src/org/eclipse/m2e/tests/internal/index/public_mirror_repo_settings.xml";

  private static final String REPO_URL_ECLIPSE = "http://repository.sonatype.org/content/repositories/eclipse";

  private IMavenConfiguration mavenConfiguration = MavenPlugin.getMavenConfiguration();

  private NexusIndexManager indexManager = (NexusIndexManager) MavenPlugin.getIndexManager();

  private RepositoryRegistry repositoryRegistry = (RepositoryRegistry) MavenPlugin.getRepositoryRegistry();

  @Override
  @Before
  public void setUp() throws Exception {
    super.setUp();
    updateRepo(REPO_URL_ECLIPSE, SETTINGS_ECLIPSE_REPO);
  }

  protected void setupPublicMirror(String publicRepoUrl, String settingsFile) throws Exception {
    final File mirroredRepoFile = new File(settingsFile);
    assertTrue(mirroredRepoFile.exists());
    mavenConfiguration.setUserSettingsFile(mirroredRepoFile.getCanonicalPath());
  }

  private IRepository getRepository(String repoUrl) {
    for(IRepository repository : repositoryRegistry.getRepositories(IRepositoryRegistry.SCOPE_SETTINGS)) {
      if(repoUrl.equals(repository.getUrl())) {
        return repository;
      }
    }
    throw new IllegalArgumentException("Repository registry does not have repository with url=" + repoUrl);
  }

  protected void updateRepo(String repoUrl, String settingsFile) throws Exception {
    setupPublicMirror(repoUrl, settingsFile);
    waitForJobsToComplete();
    IRepository repository = getRepository(repoUrl);
    indexManager.setIndexDetails(repository, NexusIndex.DETAILS_FULL, monitor);
    assertEquals(NexusIndex.DETAILS_FULL, indexManager.getIndexDetails(repository));

  }

  @Test
  public void testDisableIndex() throws Exception {
    assertEquals("Local repo should default to min details", NexusIndex.DETAILS_MIN,
        indexManager.getIndexDetails(repositoryRegistry.getLocalRepository()));
    assertEquals("Workspace repo should default to min details", NexusIndex.DETAILS_MIN,
        indexManager.getIndexDetails(repositoryRegistry.getWorkspaceRepository()));

    for(IRepository info : repositoryRegistry.getRepositories(IRepositoryRegistry.SCOPE_SETTINGS)) {
      String details = indexManager.getIndexDetails(info);
      if(!REPO_URL_ECLIPSE.equals(info.getUrl())) {
        if(!NexusIndex.DETAILS_DISABLED.equals(details)) {
          System.out.println("index not disabled: " + info.getUrl());
        }
        assertEquals("Mirrored should be disabled", NexusIndex.DETAILS_DISABLED, details);
      }
    }
  }

  @Test
  public void testProjectIndexes() throws Exception {
    updateRepo("http://central", SETTINGS_NO_MIRROR);
    String projectName = "resourcefiltering-p009";
    createExisting(projectName, "projects/resourcefiltering/p009");
    waitForJobsToComplete();
    IProject project = workspace.getRoot().getProject(projectName);
    assertNotNull(project);
    List<IRepository> repositories = repositoryRegistry.getRepositories(IRepositoryRegistry.SCOPE_PROJECT);
    //assertTrue(repositories.size() > 0);
    String projectRepo = "EclipseProjectRepo";
    boolean hasProjectRepo = false;
    for(IRepository repo : repositories) {
      if(projectRepo.equals(repo.getId())) {
        hasProjectRepo = true;
      }
    }
    assertTrue(hasProjectRepo);
  }

  @Test
  public void testProjectSpecificThenInSettings() throws Exception {
    mavenConfiguration.setUserSettingsFile("settings.xml");
    waitForJobsToComplete();

    importProject("projects/customrepo/pom.xml", new ResolverConfiguration());
    waitForJobsToComplete();

    IRepository projectRepository = getRepository("customremote", IRepositoryRegistry.SCOPE_PROJECT);
    assertNotNull(projectRepository);

    NexusIndex index = indexManager.getIndex(projectRepository);
    assertEquals(NexusIndex.DETAILS_DISABLED, index.getIndexDetails());

    assertNull(getRepository("customremote", IRepositoryRegistry.SCOPE_SETTINGS));

    mavenConfiguration.setUserSettingsFile("src/org/eclipse/m2e/tests/internal/index/customremote_settings.xml");
    waitForJobsToComplete();

    IRepository settingsRepository = getRepository("customremote", IRepositoryRegistry.SCOPE_SETTINGS);
    assertNotNull(settingsRepository);
    assertEquals(projectRepository.getUid(), settingsRepository.getUid());

    index = indexManager.getIndex(settingsRepository);
    assertEquals(NexusIndex.DETAILS_MIN, index.getIndexDetails());
  }

  /**
   * @param repositoryId
   * @param scope
   * @return
   */
  private IRepository getRepository(String repositoryId, int scope) {
    IRepository customRepository = null;
    for(IRepository repository : repositoryRegistry.getRepositories(scope)) {
      if(repositoryId.equals(repository.getId())) {
        customRepository = repository;
        break;
      }
    }
    return customRepository;
  }

  @Test
  public void testWorkspaceIndex() throws Exception {
    String projectName = "resourcefiltering-p005";
    deleteProject(projectName);
    waitForJobsToComplete();
    //not indexed at startup
    IRepository workspaceRepository = repositoryRegistry.getWorkspaceRepository();
    IndexedArtifactGroup[] rootGroups = indexManager.getRootIndexedArtifactGroups(workspaceRepository);
    if(rootGroups != null && rootGroups.length > 0) {
      //there should be no files in the workspace after the project delete
      assertTrue(rootGroups[0].getFiles() == null || rootGroups[0].getFiles().size() == 0);
    }

    //updateRepo(REPO_URL_ECLIPSE, SETTINGS_ECLIPSE_REPO);
    createExisting(projectName, "projects/resourcefiltering/p005");
    waitForJobsToComplete();

    //after the project is created, there should be the project root group
    rootGroups = indexManager.getRootIndexedArtifactGroups(workspaceRepository);
    assertTrue(rootGroups.length > 0);

    boolean containsResourceFiltering = false;
    for(IndexedArtifactGroup group : rootGroups) {
      if("resourcefiltering".equals(group.getPrefix())) {
        containsResourceFiltering = true;
      }
    }
    assertTrue(containsResourceFiltering);

    Map<String, IndexedArtifact> search = indexManager.getWorkspaceIndex().search(new SourcedSearchExpression("p005"),
        IIndex.SEARCH_ARTIFACT, 0);
    assertEquals(1, search.size());
    assertEquals("jar", search.values().iterator().next().getPackaging());

    deleteProject(projectName);
    waitForJobsToComplete();
    waitForJobsToComplete();
    assertTrue(indexManager.getWorkspaceIndex().search(new SourcedSearchExpression("p005"), IIndex.SEARCH_ARTIFACT, 0)
        .isEmpty());
  }

  //you're right. its too painfully slow

  /**
   * Authentication was causing a failure for public (non-auth) repos. This test makes sure its ok.
   */
  @Test
  public void testMngEclipse1621() throws Exception {
    final File mirroredRepoFile = new File(SETTINGS_ECLIPSE_REPO);
    assertTrue(mirroredRepoFile.exists());

    mavenConfiguration.setUserSettingsFile(mirroredRepoFile.getCanonicalPath());
    waitForJobsToComplete();

    updateIndex(REPO_URL_ECLIPSE);

    //this failed with the bug in authentication (NPE) in NexusIndexManager
    IndexedArtifactGroup[] rootGroups = indexManager.getRootIndexedArtifactGroups(getRepository(REPO_URL_ECLIPSE));
    assertTrue(rootGroups.length > 0);
  }

  private void updateIndex(String repoUrl) throws CoreException {
    IMaven maven = MavenPlugin.getMaven();
    Settings settings = maven.getSettings();
    for(ArtifactRepository repo : maven.getArtifactRepositories()) {
      if(repoUrl.equals(repo.getUrl())) {
        AuthenticationInfo auth = repositoryRegistry.getAuthenticationInfo(settings, repo.getId());
        RepositoryInfo repoInfo = new RepositoryInfo(repo.getId(), repo.getUrl(), IRepositoryRegistry.SCOPE_SETTINGS,
            auth);
        indexManager.updateIndex(repoInfo, true, monitor);
        return;
      }
    }

    throw new IllegalArgumentException("settings.xml does not define repository with url " + repoUrl);
  }

  /**
   * Simply make sure the repositories list comes back for an imported project
   * 
   * @throws Exception
   */
  @Test
  public void testIndexedArtifactGroups() throws Exception {
    final File mirroredRepoFile = new File(SETTINGS_ECLIPSE_REPO);

    assertTrue(mirroredRepoFile.exists());

    mavenConfiguration.setUserSettingsFile(mirroredRepoFile.getCanonicalPath());
    waitForJobsToComplete();
    updateIndex(REPO_URL_ECLIPSE);

    IRepository repository = getRepository(REPO_URL_ECLIPSE);
    IndexedArtifactGroup[] rootGroups = indexManager.getRootIndexedArtifactGroups(repository);
    assertTrue(rootGroups.length > 0);

    IndexedArtifactGroup iag = new IndexedArtifactGroup(repository, "org.junit");
    IndexedArtifactGroup resolveGroup = indexManager.resolveGroup(iag);
    assertTrue(resolveGroup.getFiles().size() > 0);

    IndexedArtifactGroup iag2 = new IndexedArtifactGroup(repository, "org.junit.fizzle");
    IndexedArtifactGroup resolveGroup2 = indexManager.resolveGroup(iag2);
    assertTrue(resolveGroup2.getFiles().size() == 0);

    ArtifactInfo info = new ArtifactInfo(REPO_URL_ECLIPSE, "org.junit", "junit", "3.8.1", null, "jar");
    IndexedArtifactFile indexedArtifactFile = indexManager.getIndexedArtifactFile(info);
    assertNotNull(indexedArtifactFile);
  }

  @Test
  public void testIndexedPublicArtifactGroups() throws Exception {
    // updateRepo(REPO_URL_PUBLIC, SETTINGS_PUBLIC_REPO);
    updateIndex(REPO_URL_ECLIPSE);

    // 
    Map<String, IndexedArtifact> search = indexManager.getIndex(getRepository(REPO_URL_ECLIPSE)).search(
        new UserInputSearchExpression("junit"), IIndex.SEARCH_ARTIFACT);
    IndexedArtifact ia = search.get("null : null : org.eclipse : org.eclipse.jdt.junit");
    assertNotNull(ia);
    String version = null;
    String group = null;
    String artifact = null;
    String classifier = null;
    for(IndexedArtifactFile file : ia.getFiles()) {
      if(file.version.startsWith("3.3.1")) {
        version = file.version;
        group = file.group;
        artifact = file.artifact;
        classifier = file.classifier;
      }
    }
    //trying to make sure that search and getIndexedArtifactFile stay consistent - if one
    //finds a result, the other should as well
    ArtifactKey key = new ArtifactKey(group, artifact, version, classifier);
    IndexedArtifactFile indexedArtifactFile = indexManager.getIndexedArtifactFile(getRepository(REPO_URL_ECLIPSE), key);
    assertNotNull(indexedArtifactFile);

  }

  @Test
  public void testPublicMirror() throws Exception {
    updateIndex(REPO_URL_ECLIPSE);

    List<IRepository> repositories = repositoryRegistry.getRepositories(IRepositoryRegistry.SCOPE_SETTINGS);
    assertEquals(2, repositories.size());
    IRepository eclipseRepo = null;
    for(IRepository repo : repositories) {
      if(REPO_URL_ECLIPSE.equals(repo.getUrl())) {
        eclipseRepo = repo;
      }
    }
    assertNotNull(eclipseRepo);
    assertNotNull(indexManager.getIndexingContext(eclipseRepo));

    //make sure that the junit jar can be found in the public repo
    NexusIndex index = indexManager.getIndex(eclipseRepo);
    assertNotNull(index);
    // This artifact below is NOT in "eclipse" repo!
    // so, this line is removed and replaced with a search that we know is present
    // Collection<IndexedArtifact> junitArtifact = index.find(new SourcedSearchExpression("junit"),
    // new SourcedSearchExpression("junit"), new SourcedSearchExpression("3.8.1"), new SourcedSearchExpression("jar"));

    // This below is present in repo
    // org.eclipse : org.eclipse.jdt.junit : 3.3.1.r331_v20070829
    Collection<IndexedArtifact> junitArtifact = index.find(new SourcedSearchExpression("org.eclipse"),
        new SourcedSearchExpression("org.eclipse.jdt.junit"), new UserInputSearchExpression("3.3.1"),
        new SourcedSearchExpression("jar"));
    assertTrue(junitArtifact.size() > 0);
  }

  @Test
  public void testNoMirror() throws Exception {

    final File settingsFile = new File(SETTINGS_NO_MIRROR);
    assertTrue(settingsFile.exists());

    mavenConfiguration.setUserSettingsFile(settingsFile.getCanonicalPath());
    waitForJobsToComplete();

    List<IRepository> repositories = repositoryRegistry.getRepositories(IRepositoryRegistry.SCOPE_SETTINGS);
    assertEquals(3, repositories.size());
    for(IRepository info : repositories) {
      assertTrue(info.getMirrorId() == null);
      assertTrue(info.getMirrorOf() == null);
    }

    NexusIndex workspaceIndex = indexManager.getIndex(repositoryRegistry.getWorkspaceRepository());
    assertNotNull(workspaceIndex);

    NexusIndex localIndex = indexManager.getIndex(repositoryRegistry.getLocalRepository());
    assertNotNull(localIndex);
  }

  @Test
  public void testPublicNonMirrored() throws Exception {
    final File nonMirroredRepoFile = new File(SETTINGS_PUBLIC_JBOSS_NOTMIRRORED);
    assertTrue(nonMirroredRepoFile.exists());

    mavenConfiguration.setUserSettingsFile(nonMirroredRepoFile.getCanonicalPath());
    waitForJobsToComplete();

    List<IRepository> repositories = repositoryRegistry.getRepositories(IRepositoryRegistry.SCOPE_SETTINGS);
    assertEquals(3, repositories.size());
    for(IRepository repo : repositories) {
      if("http://repository.sonatype.org/content/repositories/eclipse-snapshots/".equals(repo.getUrl())) {
        assertNotNull(indexManager.getIndexingContext(repo));
      } else if(REPO_URL_ECLIPSE.equals(repo.getUrl())) {
        assertNotNull(indexManager.getIndexingContext(repo));
      }
    }
  }

//  public void testIndexesExtensionPoint() throws Exception {
//    List<IRepository> repositories = repositoryRegistry.getRepositories(IRepositoryRegistry.SCOPE_UNKNOWN);
//
//    //
//    assertEquals(1, repositories.size());
//    
//    
//    IRepository repository = repositories.get(0);
//    assertEquals("file:testIndex", repository.getUrl());
//    
//    NexusIndex index = indexManager.getIndex(repository);
//    assertEquals(NexusIndex.DETAILS_FULL, index.getIndexDetails());
//  }
  @Test
  public void testMngEclipse1710() throws Exception {
    HttpServer httpServer = new HttpServer();
    httpServer.addResources("/", "");
    httpServer.setProxyAuth("proxyuser", "proxypass");
    httpServer.start();
    try {
      final File settingsFile = new File("target/settings-mngeclipse-1710.xml");
      FileHelpers.filterXmlFile(new File("src/org/eclipse/m2e/tests/internal/index/proxy_settings.xml"), settingsFile,
          Collections.singletonMap("@port.http@", Integer.toString(httpServer.getHttpPort())));
      assertTrue(settingsFile.exists());

      mavenConfiguration.setUserSettingsFile(settingsFile.getCanonicalPath());
      waitForJobsToComplete();

      String repoUrl = "http://bad.host/repositories/remoterepo";
      updateIndex(repoUrl);

      IndexedArtifactGroup[] rootGroups = indexManager.getRootIndexedArtifactGroups(getRepository(repoUrl));
      assertTrue(rootGroups.length > 0);
    } finally {
      httpServer.stop();
    }
  }

  @Test
  public void testMngEclipse1907() throws Exception {
    mavenConfiguration.setUserSettingsFile(new File("settings.xml").getCanonicalPath());

    IProject project = importProject("projects/projectimport/p001/pom.xml", new ResolverConfiguration());
    waitForJobsToComplete();

    // make facade shallow as it would be when the workspace was just started and its state deserialized
    IMavenProjectFacade facade = MavenPlugin.getMavenProjectRegistry().getProject(project);
    deserializeFromWorkspaceState(facade);

    project.delete(true, true, new NullProgressMonitor());

    MavenProjectChangedEvent event = new MavenProjectChangedEvent(facade.getPom(),
        MavenProjectChangedEvent.KIND_REMOVED, MavenProjectChangedEvent.FLAG_NONE, facade, null);
    ((NexusIndexManager) MavenPlugin.getIndexManager()).mavenProjectChanged(new MavenProjectChangedEvent[] {event},
        new NullProgressMonitor());
  }

  @Test
  public void testFetchIndexFromRepositoryWithAuthentication() throws Exception {
    HttpServer httpServer = new HttpServer();
    httpServer.addResources("/", "");
    httpServer.addUser("testuser", "testpass", "index-reader");
    httpServer.addSecuredRealm("/*", "index-reader");
    httpServer.start();
    try {
      final File settingsFile = new File("target/settings-index-with-auth.xml");
      FileHelpers.filterXmlFile(new File("src/org/eclipse/m2e/tests/internal/index/auth_settings.xml"), settingsFile,
          Collections.singletonMap("@port.http@", Integer.toString(httpServer.getHttpPort())));
      assertTrue(settingsFile.exists());

      mavenConfiguration.setUserSettingsFile(settingsFile.getCanonicalPath());
      waitForJobsToComplete();

      String repoUrl = httpServer.getHttpUrl() + "/repositories/remoterepo";
      updateIndex(repoUrl);
      IndexedArtifactGroup[] rootGroups = indexManager.getRootIndexedArtifactGroups(getRepository(repoUrl));
      assertTrue(rootGroups.length > 0);
    } finally {
      httpServer.stop();
    }
  }

}
