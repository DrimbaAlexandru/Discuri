package ProjectStatics;

import AudioDataSource.ADS_Utils;
import AudioDataSource.CachedADS.CachedAudioDataSource;
import AudioDataSource.Cached_ADS_Manager;
import AudioDataSource.FileADS.FileAudioSourceFactory;
import AudioDataSource.IAudioDataSource;
import AudioDataSource.VersionedADS.AudioDataSourceVersion;
import AudioDataSource.VersionedADS.VersionedAudioDataSource;
import Exceptions.DataSourceException;
import Exceptions.DataSourceExceptionCause;
import MarkerFile.MarkerFile;
import MarkerFile.Marking;
import SignalProcessing.Effects.IEffect;
import Utils.Interval;

import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.text.ParseException;

/**
 * Created by Alex on 17.06.2018.
 */
public class ProjectManager
{
    private static VersionedAudioDataSource versionedADS = null;
    private static MarkerFile markerFile = new MarkerFile();
    private static CachedAudioDataSource cache = null;

    public static void new_project_from_audio_file( String filepath ) throws DataSourceException
    {
        if( versionedADS != null )
        {
            cache.flushAll();
            versionedADS.dispose();
        }
        versionedADS = new VersionedAudioDataSource( filepath );

        if( cache == null )
        {
            cache = new CachedAudioDataSource( versionedADS.get_current_version(), ProjectStatics.getDefault_cache_size(), ProjectStatics.getDefault_cache_page_size() );
        }
        else
        {
            cache.setDataSource( versionedADS.get_current_version() );
        }
    }

    public static void apply_effect( IEffect effect, Interval interval ) throws DataSourceException
    {
        if( versionedADS == null )
        {
            throw new DataSourceException( "Current project is empty", DataSourceExceptionCause.INVALID_STATE );
        }
        AudioDataSourceVersion sourceVersion = versionedADS.get_current_version();
        AudioDataSourceVersion destinationVersion = versionedADS.create_new();
        CachedAudioDataSource destCache = new CachedAudioDataSource( destinationVersion, ProjectStatics.getDefault_cache_size(), ProjectStatics.getDefault_cache_page_size() );
        cache.flushAll();
        effect.apply( cache, destCache, interval );
        cache = destCache;
    }

    public static void undo() throws DataSourceException
    {
        if( versionedADS == null )
        {
            throw new DataSourceException( "Current project is empty", DataSourceExceptionCause.INVALID_STATE );
        }
        versionedADS.undo();
        cache.setDataSource( versionedADS.get_current_version() );
    }

    public static void redo() throws DataSourceException
    {
        if( versionedADS == null )
        {
            throw new DataSourceException( "Current project is empty", DataSourceExceptionCause.INVALID_STATE );
        }
        versionedADS.redo();
        cache.setDataSource( versionedADS.get_current_version() );
    }

    public static void export_project( String filepath ) throws DataSourceException
    {
        if( versionedADS == null )
        {
            throw new DataSourceException( "Current project is empty", DataSourceExceptionCause.INVALID_STATE );
        }
        IAudioDataSource file = FileAudioSourceFactory.createFile( filepath, cache.get_channel_number(), cache.get_sample_rate(), 2 );
        ADS_Utils.copyToADS( cache, file );
        file.close();
    }

    public static void clear_all_markings()
    {
        markerFile.clear_all_markings();
    }

    public static void load_marker_file( String filepath ) throws FileNotFoundException, ParseException
    {
        markerFile.add_from_file( filepath );
    }

    public static void export_marker_file( String filepath ) throws IOException
    {
        markerFile.writeMarkingsToFile( new FileWriter( filepath ) );
    }

    public static void add_marking( Marking m )
    {
        markerFile.addMark( m.get_first_marked_sample(), m.get_last_marked_sample(), m.getChannel() );
    }

    public static void remove_marking( Marking m )
    {
        markerFile.deleteMark( m.get_first_marked_sample(), m.get_last_marked_sample(), m.getChannel() );
    }

    public static void discard_project() throws DataSourceException
    {
        Cached_ADS_Manager.finish_all_caches();
        if( versionedADS != null )
        {
            cache.flushAll();
            versionedADS.dispose();
        }
    }

    public static CachedAudioDataSource getCache()
    {
        return cache;
    }

    public static MarkerFile getMarkerFile()
    {
        return markerFile;
    }
}
