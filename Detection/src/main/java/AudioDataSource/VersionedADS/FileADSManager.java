package AudioDataSource.VersionedADS;

import AudioDataSource.CachedADS.CachedAudioDataSource;
import AudioDataSource.FileADS.FileAudioSourceFactory;
import AudioDataSource.FileADS.IFileAudioDataSource;
import Exceptions.DataSourceException;
import Exceptions.DataSourceExceptionCause;
import ProjectManager.ProjectStatics;
import Utils.MyPair;
import Utils.Tuple_3;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;

import static Exceptions.DataSourceExceptionCause.INVALID_PARAMETER;
import static Exceptions.DataSourceExceptionCause.IO_ERROR;
import static java.nio.file.Files.deleteIfExists;

class FileADSManager
{
    private static int file_id = 1;

    /* structure that keeps track of each audio file's usage within the ADS versions. */
    private static TreeMap< String, Tuple_3< IFileAudioDataSource, List< Integer >, Boolean > > file_references = new TreeMap<>();

    public static void associate_file_with_version( String file, Boolean is_temp, int version ) throws DataSourceException
    {
        List<Integer> file_associated_versions;

        if( !file_references.containsKey( file ) )
        {
            if( is_temp == null )
            {
                throw new DataSourceException( INVALID_PARAMETER );
            }
            file_references.put( file, new Tuple_3<>( FileAudioSourceFactory.fromFile( file ), new ArrayList<>(), is_temp ) );
        }

        file_associated_versions = file_references.get( file ).e2;

        if( !file_associated_versions.contains( version ) )
        {
            file_associated_versions.add( version );
        }
    }

    public static void deassociate_file_from_version( String file, int version ) throws DataSourceException
    {
        Tuple_3< IFileAudioDataSource, List< Integer >, Boolean > file_ref = file_references.get( file );

        if( file_ref == null )
        {
            return;
        }

        file_ref.e2.remove( Integer.valueOf( version ) );

        if( file_ref.e2.size() == 0 )
        {
            file_references.remove( file );
            file_ref.e1.close();

            /* Delete the file if it is a temporary file and no versions need it anymore */
            if( file_ref.e3 )
            {
                try
                {
                    deleteIfExists( new File( file ).toPath() );
                }
                catch( IOException e )
                {
                    throw new DataSourceException( e.getMessage(), IO_ERROR );
                }
            }
        }
    }

    public static IFileAudioDataSource get_file_ADS( String file ) throws DataSourceException
    {
        Tuple_3< IFileAudioDataSource, List< Integer >, Boolean > file_ref = file_references.get( file );

        if( file_ref == null )
        {
            throw new DataSourceException( DataSourceExceptionCause.INVALID_STATE );
        }

        return file_ref.e1;
    }

    public static String gimme_a_new_files_name()
    {
        return "chunk_" + String.format( "%06d", file_id++ ) + ".au";
    }
}