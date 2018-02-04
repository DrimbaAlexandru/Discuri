package ProjectStatics;

import AudioDataSource.ADCache.CachedAudioDataSource;
import AudioDataSource.Exceptions.DataSourceException;
import AudioDataSource.Exceptions.DataSourceExceptionCause;
import AudioDataSource.FileADS.FileAudioSourceFactory;
import AudioDataSource.FileADS.IFileAudioDataSource;
import AudioDataSource.FileADS.WAVFileAudioSource;
import AudioDataSource.IAudioDataSource;
import AudioDataSource.VersionedADS.VersionedAudioDataSource;
import MarkerFile.MarkerFile;
import SignalProcessing.Effects.IEffect;

import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by Alex on 10.01.2018.
 */
public class ProjectStatics
{
    private static VersionedAudioDataSource versionedADS = null;
    private static MarkerFile markerFile = new MarkerFile( null );
    private static IEffect last_applied_effect = null;
    private static List< IEffect > effectList = new ArrayList<>();
    private static int default_cache_size = 44100;
    private static int default_cache_page_size = 2048;

    public static void loadAudioFile( String filePath ) throws DataSourceException
    {
        if( versionedADS != null )
        {
            versionedADS.dispose();
        }

        versionedADS = new VersionedAudioDataSource( filePath );
    }

    public static VersionedAudioDataSource getVersionedADS()
    {
        return versionedADS;
    }

    public static void loadMarkerFile( String path ) throws FileNotFoundException, ParseException
    {
        markerFile = MarkerFile.fromFile( path );
    }

    public static MarkerFile getMarkerFile()
    {
        return markerFile;
    }

    public static void saveMarkerFile( String path ) throws IOException
    {
        markerFile.writeMarkingsToFile( new FileWriter( path ) );
    }

    public static void setLast_applied_effect( IEffect last_applied_effect )
    {
        ProjectStatics.last_applied_effect = last_applied_effect;
    }

    public static IEffect getLast_applied_effect()
    {
        return last_applied_effect;
    }

    public static void registerEffect( IEffect effect )
    {
        effectList.add( effect );
    }

    public static List< IEffect > getEffectList()
    {
        return effectList;
    }

    public static int getDefault_cache_page_size()
    {
        return default_cache_page_size;
    }

    public static int getDefault_cache_size()
    {
        return default_cache_size;
    }
}
