/*******************************************************************************
 * Copyright (c) 2008 Sonatype, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/

package org.maven.ide.eclipse.editor.pom;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.File;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.Path;
import org.eclipse.swt.SWT;
import org.eclipse.swtbot.swt.finder.widgets.SWTBotText;
import org.junit.Test;
import org.maven.ide.eclipse.integration.tests.common.ContextMenuHelper;


/**
 * @author Eugene Kuleshov
 * @author Anton Kraev
 */
public class PomEditorTest extends PomEditorTestBase {

  @Test
  public void testUpdatingArtifactIdInXmlPropagatedToForm() throws Exception {
    openPomFile(TEST_POM_POM_XML);

    selectEditorTab(TAB_POM_XML);
    replaceText("test-pom", "test-pom1");

    selectEditorTab(TAB_OVERVIEW);
    assertTextValue("artifactId", "test-pom1");
  }

  @Test
  public void testFormToXmlAndXmlToFormInParentArtifactId() throws Exception {
    openPomFile(TEST_POM_POM_XML);

    // test FORM->XML and XML->FORM update of parentArtifactId
    selectEditorTab(TAB_OVERVIEW);

    bot.section("Parent").expand();
    setTextValue("parentArtifactId", "parent2");

    selectEditorTab(TAB_POM_XML);
    replaceTextWithWrap("parent2", "parent3", true);

    selectEditorTab(TAB_OVERVIEW);
    assertTextValue("parentArtifactId", "parent3");
  }

  @Test
  public void testNewSectionCreation() throws Exception {
    openPomFile(TEST_POM_POM_XML);

    bot.section("Organization").expand();

    setTextValue("organizationName", "org.foo");

    selectEditorTab(TAB_POM_XML);
    replaceTextWithWrap("org.foo", "orgfoo1", true);

    selectEditorTab(TAB_OVERVIEW);
    assertTextValue("organizationName", "orgfoo1");
  }

  @Test
  public void testUndoRedo() throws Exception {
    openPomFile(TEST_POM_POM_XML);

    SWTBotText text = bot.textWithName("organizationName");
    text.setFocus();
    text.setText("");
    text.typeText("orgfoo");
    bot.textWithName("organizationUrl").setFocus();
    text.setFocus();
    text.typeText("1");

    // test undo
    text.pressShortcut(SWT.CONTROL, 'z');
    assertTextValue("organizationName", "orgfoo");
    // test redo
    text.pressShortcut(SWT.CONTROL, 'y');
    assertTextValue("organizationName", "orgfoo1");
  }

  @Test
  public void testDeletingScmSectionInXmlPropagatedToForm() throws Exception {
    openPomFile(TEST_POM_POM_XML);

    selectEditorTab(TAB_OVERVIEW);
    bot.section("SCM").expand();

    setTextValue("scmUrl", "http://m2eclipse");
    assertTextValue("scmUrl", "http://m2eclipse");
    selectEditorTab(TAB_POM_XML);
    delete("<scm>", "</scm>");
    selectEditorTab(TAB_OVERVIEW);
    assertTextValue("scmUrl", "");
    selectEditorTab(TAB_POM_XML);
    delete("<organization>", "</organization>");
    selectEditorTab(TAB_OVERVIEW);
    assertTextValue("organizationName", "");
    setTextValue("scmUrl", "http://m2eclipse");
    assertTextValue("scmUrl", "http://m2eclipse");
  }

  @Test
  public void testExternalModification() throws Exception {
    openPomFile(TEST_POM_POM_XML);

    // externally replace file contents
    IWorkspaceRoot root = ResourcesPlugin.getWorkspace().getRoot();
    IFile file = root.getFile(new Path(TEST_POM_POM_XML));
    File f = new File(file.getLocation().toOSString());
    String text = getEditorText();
    setContents(f, text.replace("parent3", "parent4"));

    // reload the file
    selectProject(PROJECT_NAME).expandNode(PROJECT_NAME).getNode("pom.xml").doubleClick();

    bot.shell("File Changed").activate();
    bot.button("Yes").click();

    assertTextValue("parentArtifactId", "parent4");

    // verify that value changed in xml and in the form
    selectEditorTab(TAB_POM_XML);
    String editorText = getEditorText();
    assertTrue(editorText, editorText.contains("<artifactId>parent4</artifactId>"));

    // XXX verify that value changed on a page haven't been active before
  }

  @Test
  public void testNewEditorIsClean() throws Exception {
    MavenPomEditor editor = openPomFile(TEST_POM_POM_XML);

    // close/open the file 
    bot.editorByTitle(TEST_POM_POM_XML).close();

    openPomFile(TEST_POM_POM_XML);

    // test the editor is clean
    waitForEditorDirtyState(editor, false);
  }

  //MNGECLIPSE-874
  @Test
  public void testUndoAfterSave() throws Exception {
    MavenPomEditor editor = openPomFile(TEST_POM_POM_XML);

    // make a change 
    selectEditorTab(TAB_POM_XML);
    replaceText("parent4", "parent7");
    selectEditorTab(TAB_OVERVIEW);

    //save file
    save();

    // test the editor is clean
    waitForEditorDirtyState(editor, false);

    // undo change
    bot.textWithName("parentArtifactId").pressShortcut(SWT.CONTROL, 'z');

    // test the editor is dirty
    waitForEditorDirtyState(editor, true);

    //test the value
    assertTextValue("parentArtifactId", "parent4");

    //save file
    save();
  }

  @Test
  public void testAfterUndoEditorIsClean() throws Exception {
    MavenPomEditor editor = openPomFile(TEST_POM_POM_XML);

    // make a change 
    selectEditorTab(TAB_POM_XML);
    replaceText("parent4", "parent7");
    selectEditorTab(TAB_OVERVIEW);
    // undo change
    bot.activeEditor().toTextEditor().pressShortcut(SWT.CONTROL, 'z');

    // test the editor is clean
    waitForEditorDirtyState(editor, false);
  }

  @Test
  public void testEmptyFile() throws Exception {
    String name = PROJECT_NAME + "/test.pom";
    createFile(name, "");
    openPomFile(name);

    assertTextValue("artifactId", "");
    setTextValue("artifactId", "artf1");
    selectEditorTab(TAB_POM_XML);
    replaceText("artf1", "artf2");
    selectEditorTab(TAB_OVERVIEW);
    assertTextValue("artifactId", "artf2");

  }

  @Test
  public void testDiscardedFileDeletion() throws Exception {
    String name = PROJECT_NAME + "/another.pom";
    createFile(name, "");
    openPomFile(name);

    bot.editorByTitle(name).close();

    openPomFile(name);
    setTextValue("groupId", "abc");

    bot.activeEditor().toTextEditor().pressShortcut(SWT.CONTROL, 'w');
    bot.shell("Save Resource").activate();
    bot.button("No").click();

    selectProject(PROJECT_NAME).expandNode(PROJECT_NAME).getNode("another.pom").select();
    ContextMenuHelper.clickContextMenu(bot.tree(), "Delete");
    bot.shell("Confirm Delete").activate();
    bot.button("OK").click();

    IFile file = ResourcesPlugin.getWorkspace().getRoot().getFile(new Path(name));
    waitForFileToDisappear(file);
  }

  // MNGECLIPSE-833
  @Test
  public void testSaveAfterPaste() throws Exception {
    String name = PROJECT_NAME + "/another2.pom";
    String str = "<project xmlns=\"http://maven.apache.org/POM/4.0.0\" " //
        + "xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" " //
        + "xsi:schemaLocation=\"http://maven.apache.org/POM/4.0.0 http://maven.apache.org/maven-v4_0_0.xsd\">" //
        + "<modelVersion>4.0.0</modelVersion>" //
        + "<groupId>test</groupId>" //
        + "<artifactId>parent</artifactId>" //
        + "<packaging>pom</packaging>" //
        + "<version>0.0.1-SNAPSHOT</version>" //
        + "</project>";
    createFile(name, str);
//		IFile file = root.getFile(new Path(name));
//		file.create(new ByteArrayInputStream(str.getBytes()), true, null);

    MavenPomEditor editor = openPomFile(name);

    selectEditorTab(TAB_POM_XML);
    waitForEditorDirtyState(editor, false);
    findText("</project>");
    bot.activeEditor().toTextEditor().pressShortcut(SWT.NONE, SWT.LEFT, (char) 0);

    copy("<properties><sample>sample</sample></properties>");
    bot.activeEditor().toTextEditor().pressShortcut(SWT.CONTROL, 'v');
    waitForEditorDirtyState(editor, true);

    save();
    waitForEditorDirtyState(editor, false);
    bot.activeEditor().toTextEditor().pressShortcut(SWT.CONTROL, 'w');
  }

  // MNGECLIPSE-835
  @Test
  public void testModulesEditorActivation() throws Exception {
    MavenPomEditor editor = openPomFile(TEST_POM_POM_XML);

    bot.activeEditor().toTextEditor().pressShortcut(SWT.CONTROL, 'm');

    bot.section("Parent").expand();
    // getUI().click(new SWTWidgetLocator(Label.class, "Properties"));

    selectEditorTab(TAB_OVERVIEW);

    bot.button("Create...").click();
    bot.table().getTableItem("?").select();

    selectEditorTab(TAB_POM_XML);
    replaceTextWithWrap(">?<", ">foo1<", true);

    save();

    selectEditorTab(TAB_OVERVIEW);
    bot.table().getTableItem("foo1").select();

    bot.activeEditor().toTextEditor().pressShortcut(SWT.CONTROL, 'm');

    // test the editor is clean
    waitForEditorDirtyState(editor, false);
  }

  private void waitForEditorDirtyState(MavenPomEditor editor, boolean dirtyState) {
    for(int n = 0; n < 10; n++ ) {
      if(dirtyState == editor.isDirty()) {
        return;
      }
      bot.sleep(1000);
    }
    fail("Timed out waiting for editor dirty state: " + dirtyState);
  }

  private void waitForFileToDisappear(IFile file) {
    for(int n = 0; n < 10; n++ ) {
      if(!file.exists()) {
        return;
      }
      bot.sleep(1000);
    }
    fail("Timed out waiting for file to be deleted: " + file);
  }
}
