package org.eclipse.m2e.test.lifecyclemapping.plugin;

import java.io.File;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;

/**
 * @goal test-goal-with-parameters
 */
public class DummyMojoWithParameters extends AbstractMojo
{
    /** @parameter default-value="parameter1-default-value" */
    private String parameter1;
  
    /** @parameter */
    private File file;
    
    /** @parameter */
    private File[] fileArray;
    
    public void execute()
        throws MojoExecutionException, MojoFailureException
    {
    }
}
