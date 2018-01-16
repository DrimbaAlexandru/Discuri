package AudioDataSource.FileADS;

import AudioDataSource.Exceptions.DataSourceException;
import AudioDataSource.Exceptions.DataSourceExceptionCause;

/**
 * Created by Alex on 19.12.2017.
 */
public class FileAudioSourceFactory
{
    private static String getExtension( String path )
    {
        String file_name;
        path = path.replaceAll( "\\\\", "/" );
        file_name = path.substring( path.lastIndexOf( "/" ) + 1 );
        return file_name.substring( file_name.lastIndexOf( '.' ) + 1 );

    }

    public static IFileAudioDataSource fromFile( String path ) throws DataSourceException
    {
        String ext = getExtension( path ).toLowerCase();
        switch( ext )
        {
            case "wav":
                return new WAVFileAudioSource( path );
            case "au":
                return new AUFileAudioSource( path );
            default:
                throw new DataSourceException( "File type not supported", DataSourceExceptionCause.UNSUPPORTED_FILE_FORMAT );
        }
    }

    public static IFileAudioDataSource createFile( String path, int channel_number, int sample_rate, int byte_depth ) throws DataSourceException
    {
        String ext = getExtension( path ).toLowerCase();
        switch( ext )
        {
            case "wav":
                return new WAVFileAudioSource( path, channel_number, sample_rate, byte_depth );
            case "au":
                return new AUFileAudioSource( path, channel_number, sample_rate, byte_depth );
            default:
                throw new DataSourceException( "File type not supported", DataSourceExceptionCause.UNSUPPORTED_FILE_FORMAT );
        }
    }
}
