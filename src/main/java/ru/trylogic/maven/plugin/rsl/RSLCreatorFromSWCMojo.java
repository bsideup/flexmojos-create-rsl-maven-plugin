package ru.trylogic.maven.plugin.rsl;

import static net.flexmojos.oss.plugin.common.FlexExtension.SWC;
import static net.flexmojos.oss.plugin.common.FlexExtension.SWF;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import org.apache.commons.io.FilenameUtils;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.DefaultArtifact;
import org.apache.maven.artifact.handler.manager.ArtifactHandlerManager;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.codehaus.plexus.util.IOUtil;
import net.flexmojos.oss.compiler.IDigestConfiguration;
import net.flexmojos.oss.plugin.compiler.attributes.MavenRuntimeException;
import net.flexmojos.oss.util.PathUtil;

/**
 * Goal which run post-link SWF optimization on swc files. This goal is used to produce RSL files.
 *
 * @author Sergey Egorov (bsideup@gmail.com)
 * @since 4.0
 * @phase package
 * @goal create-rsl-for-items
 */
public class RSLCreatorFromSWCMojo
        extends AbstractOptimizerMojo
{

    /**
     *
     * @parameter
     */
    protected File file;

    /**
     *
     * @parameter expression="${project.groupId}"
     */
    protected String groupId;

    /**
     *
     * @parameter
     * @required
     */
    protected String artifactId;

    /**
     *
     * @parameter expression="${project.version}"
     */
    protected String version;

    /**
     * Optimized RSLs strip out any debugging information such as line numbers. This results in a smaller file, which
     * leads to shorter load times but makes it more difficult to read stacktrace errors as they contain no line
     * numbers.
     * <p>
     * Equivalent to optimizer execution
     * </p>
     *
     * @parameter default-value="true" expression="${flex.optimizeRsl}"
     */
    private boolean optimizeRsl;

    /**
     * When true it does update the swc digester information, doesn't make any sense not do it
     * <p>
     * Equivalent to digester execution
     * </p>
     *
     * @parameter default-value="true" expression="${flex.updateSwcDigest}"
     */
    private boolean updateSwcDigest;

    /**
     * When true won't create a RSL (swf) for this project
     *
     * @parameter default-value="false" expression="${flex.skipRSLCreation}"
     */
    private boolean skipRSLCreation;

    /**
     * @component
     */
    private ArtifactHandlerManager artifactHandlerManager;
    
    public void execute()
            throws MojoExecutionException, MojoFailureException
    {
        
        if ( file == null )
        {
            getLog().warn( "Skipping RSL creator, no SWC attached to this project." );
            return;
        }
        
        getLog().info("Optimizing " + file.getAbsolutePath());

        File input = optimize();

        if ( updateSwcDigest )
        {
            int result;
            try
            {
                result = compiler.digest( getDigestConfiguration( input ), true ).getExitCode();
            }
            catch ( Exception e )
            {
                throw new MojoExecutionException( e.getMessage(), e );
            }

            if ( result != 0 )
            {
                throw new MojoFailureException( "Got " + result + " errors building project, check logs" );
            }
        }

        Artifact sourceSwc = new DefaultArtifact(groupId, artifactId, version, null, SWC, null, artifactHandlerManager.getArtifactHandler(SWC));
        sourceSwc.setFile(file);
        sourceSwc.setResolved(true);

        getLog().debug("attaching original swc Artifact" + sourceSwc.getId());

        project.addAttachedArtifact(sourceSwc);

        Artifact generatedSwf = new DefaultArtifact(groupId, artifactId, version, null, SWF, null, artifactHandlerManager.getArtifactHandler(SWF));
        generatedSwf.setFile(new File(getOutput()));
        generatedSwf.setResolved(true);

        getLog().debug("attaching swf artifact " + generatedSwf.getId());

        project.addAttachedArtifact(generatedSwf);
        
    }

    @Override
    public String getOutput()
    {
        return PathUtil.path( new File( build.getDirectory(), groupId + "/" + artifactId + "-" + version + ".swf" ) );
    }

    protected File optimize( File input )
            throws MojoFailureException, MojoExecutionException
    {
        if ( optimizeRsl )
        {
            getLog().debug( "Optimizing" );
            final File output = new File( project.getBuild().getOutputDirectory(), "optimized.swf" );
            optimize( input, output );
            input = output;
        }
        return input;
    }

    public IDigestConfiguration getDigestConfiguration( final File input )
    {
        return new IDigestConfiguration()
        {

            public File getSwcPath()
            {
                return file;
            }

            public Boolean getSigned()
            {
                return false;
            }

            public File getRslFile()
            {
                return input;
            }
        };
    }

    @Override
    public String getInput()
    {
        getLog().debug( "attempting to optimize: " + file.getName() );

        File bkpOriginalFile = new File( build.getDirectory(), FilenameUtils.removeExtension(file.getName()) + ".swf" );
        try
        {
            ZipFile zipFile = new ZipFile( file );
            ZipEntry entry = zipFile.getEntry( "library.swf" );
            if ( entry == null )
            {
                throw new MavenRuntimeException( "Invalid SWC file. Library.swf not found. " + file );
            }
            InputStream inputSWF = zipFile.getInputStream( entry );
            IOUtil.copy( inputSWF, new FileOutputStream( bkpOriginalFile ) );
        }
        catch ( Exception e )
        {
            throw new MavenRuntimeException( e.getMessage() + ": " + PathUtil.path( file ), e );
        }

        return PathUtil.path( bkpOriginalFile );
    }

}
