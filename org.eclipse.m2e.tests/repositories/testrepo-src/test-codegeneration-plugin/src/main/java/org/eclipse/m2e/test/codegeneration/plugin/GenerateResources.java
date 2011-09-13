package org.eclipse.m2e.test.codegeneration.plugin;

import java.io.File;
import java.io.IOException;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.codehaus.plexus.util.DirectoryScanner;
import org.codehaus.plexus.util.FileUtils;

/**
 * @goal generate-resources-with-source-directory
 */
public class GenerateResources
    extends AbstractMojo
{
    /**
     * @parameter default-value="${basedir}/src/main/test-resources"
     */
    private File sourceDirectory;

    /**
     * @parameter default-value="${project.build.directory}/generated-sources/test-resources"
     */
    private File outputDirectory;

    public void execute()
        throws MojoExecutionException, MojoFailureException
    {
        DirectoryScanner ds = new DirectoryScanner();
        ds.setBasedir( sourceDirectory );
        ds.setIncludes( new String[] { "**" } );
        ds.scan();

        String[] relpaths = ds.getIncludedFiles();

        if ( relpaths == null )
        {
            return;
        }

        outputDirectory.mkdirs();

        for ( String relpath : relpaths )
        {
            File src = new File( sourceDirectory, relpath );
            File dst = new File( outputDirectory, relpath );

            try
            {
                String data = FileUtils.fileRead( src );
                FileUtils.fileAppend( dst.getCanonicalPath(), data );
            }
            catch ( IOException e )
            {
                getLog().warn( e );
            }
        }
    }
}
