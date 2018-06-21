package ProjectManager;

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
import SignalProcessing.Effects.Copy_to_ADS;
import SignalProcessing.Effects.Create_Marker_File;
import SignalProcessing.Effects.IEffect;
import Utils.Interval;

import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.text.ParseException;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Created by Alex on 17.06.2018.
 */
public class ProjectManager
{
    private static VersionedAudioDataSource versionedADS = null;
    private static MarkerFile markerFile = new MarkerFile();
    private static CachedAudioDataSource cache = null;

    private static ReentrantLock mutex = new ReentrantLock();

    public static void lock_access()
    {
        mutex.lock();
    }

    public static void release_access()
    {
        mutex.unlock();
    }

    public static void check_thread_access() throws DataSourceException
    {
        if( !mutex.isHeldByCurrentThread() || mutex.hasQueuedThreads() )
        {
            throw new DataSourceException( "Concurrency exception! The thread " + Thread.currentThread().getName() + " tried to access the ProjectManager without holding its mutex", DataSourceExceptionCause.CONCURRENCY_VIOLATION );
        }
    }

    public static void new_project_from_audio_file( String filepath ) throws DataSourceException
    {
        check_thread_access();
        if( versionedADS != null )
        {
            cache.flushAll();
            versionedADS.dispose();
        }
        versionedADS = new VersionedAudioDataSource( filepath );

        if( cache == null )
        {
            cache = new CachedAudioDataSource( versionedADS.get_current_version(), ProjectStatics.getProject_cache_size(), ProjectStatics.getProject_cache_page_size() );
        }
        else
        {
            cache.setDataSource( versionedADS.get_current_version() );
        }
    }

    public static void apply_effect( IEffect effect, Interval interval ) throws DataSourceException
    {
        check_thread_access();
        if( versionedADS == null )
        {
            throw new DataSourceException( "Current project is empty", DataSourceExceptionCause.INVALID_STATE );
        }
        AudioDataSourceVersion sourceVersion = versionedADS.get_current_version();
        cache.flushAll();
        AudioDataSourceVersion destinationVersion = versionedADS.create_new();
        CachedAudioDataSource destCache = new CachedAudioDataSource( destinationVersion, ProjectStatics.getProject_cache_size(), ProjectStatics.getProject_cache_page_size() );
        effect.apply( cache, destCache, interval );
        cache = destCache;
    }

    public static void undo() throws DataSourceException
    {
        check_thread_access();
        if( versionedADS == null )
        {
            throw new DataSourceException( "Current project is empty", DataSourceExceptionCause.INVALID_STATE );
        }
        versionedADS.undo();
        cache.setDataSource( versionedADS.get_current_version() );
    }

    public static void redo() throws DataSourceException
    {
        check_thread_access();
        if( versionedADS == null )
        {
            throw new DataSourceException( "Current project is empty", DataSourceExceptionCause.INVALID_STATE );
        }
        versionedADS.redo();
        cache.setDataSource( versionedADS.get_current_version() );
    }

    public static void export_project( String filepath ) throws DataSourceException
    {
        check_thread_access();
        if( versionedADS == null )
        {
            throw new DataSourceException( "Current project is empty", DataSourceExceptionCause.INVALID_STATE );
        }
        IAudioDataSource file = FileAudioSourceFactory.createFile( filepath, cache.get_channel_number(), cache.get_sample_rate(), 2 );
        new Copy_to_ADS().apply( cache, file, new Interval( 0, cache.get_sample_number() ) );
        file.close();
    }

    public static void clear_all_markings() throws DataSourceException
    {
        check_thread_access();
        markerFile.clear_all_markings();
    }

    public static void add_from_marker_file( String filepath ) throws FileNotFoundException, ParseException, DataSourceException
    {
        check_thread_access();
        markerFile.add_from_file( filepath );
    }

    public static void export_marker_file( String filepath ) throws IOException, DataSourceException
    {
        check_thread_access();
        markerFile.writeMarkingsToFile( new FileWriter( filepath ) );
    }

    public static void add_marking( Marking m ) throws DataSourceException
    {
        check_thread_access();
        markerFile.addMark( m.get_first_marked_sample(), m.get_last_marked_sample() - 1, m.getChannel() );
    }

    public static void remove_marking( Marking m ) throws DataSourceException
    {
        check_thread_access();
        markerFile.deleteMark( m.get_first_marked_sample(), m.get_last_marked_sample() - 1, m.getChannel() );
    }

    public static void discard_project() throws DataSourceException
    {
        check_thread_access();
        if( versionedADS != null )
        {
            cache.flushAll();
            versionedADS.dispose();
        }
        Cached_ADS_Manager.finish_all_caches();
    }

    public static CachedAudioDataSource getCache() throws DataSourceException
    {
        check_thread_access();
        return cache;
    }

    public static MarkerFile getMarkerFile() throws DataSourceException
    {
        check_thread_access();
        return markerFile;
    }

    public static void generate_markings( Create_Marker_File effect, Interval interval ) throws DataSourceException
    {
        check_thread_access();
        if( versionedADS == null )
        {
            throw new DataSourceException( "Current project is empty", DataSourceExceptionCause.INVALID_STATE );
        }
        effect.apply( getCache(), null, interval );
    }
}
