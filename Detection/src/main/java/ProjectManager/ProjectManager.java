package ProjectManager;

import AudioDataSource.CachedADS.CachedAudioDataSource;
import AudioDataSource.Cached_ADS_Manager;
import AudioDataSource.VersionedADS.AudioDataSourceVersion;
import AudioDataSource.VersionedADS.VersionedAudioDataSource;
import Exceptions.DataSourceException;
import Exceptions.DataSourceExceptionCause;
import MarkerFile.MarkerFile;
import MarkerFile.Marking;
import SignalProcessing.Effects.IEffect;
import SignalProcessing.SampleClassifier.AIDamageRecognition;
import SignalProcessing.SampleClassifier.RemoteAIServer.IOP_IPC_stdio;
import SignalProcessing.SampleClassifier.RemoteAIServer.IOP_msg_struct_type;
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

    private static IOP_IPC_stdio classifier_ipc = null;

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
        Cached_ADS_Manager.finish_all_caches();

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

    public static void apply_read_only_effect( IEffect effect, Interval interval ) throws DataSourceException
    {
        check_thread_access();
        if( versionedADS == null )
        {
            throw new DataSourceException( "Current project is empty", DataSourceExceptionCause.INVALID_STATE );
        }
        effect.apply( getCache(), null, interval );
    }

    public static void start_classifier_process() throws IOException, InterruptedException
    {
        String script_exec = "python -u " + "\"" + ProjectStatics.getPython_classifier_script_path() + "\"";
        stop_classifier_process( true );
        classifier_ipc = new IOP_IPC_stdio( script_exec );
    }

    public static IOP_IPC_stdio get_classifier_ipc()
    {
        if( classifier_ipc != null && !classifier_ipc.getProcess().isAlive() )
        {
            classifier_ipc = null;
        }
        return classifier_ipc;

    }

    public static void stop_classifier_process( boolean force ) throws IOException, InterruptedException
    {
        if( classifier_ipc == null )
        {
            return;
        }

        if( !force )
        {
            IOP_msg_struct_type msg = new IOP_msg_struct_type();
            msg.ID = AIDamageRecognition.IOP_MSG_ID_CMD;
            msg.subID = AIDamageRecognition.IOP_MSG_SUBID_CMD_TERMINATE;
            msg.size = 0;

            classifier_ipc.IOP_put_frame( msg );

            // Wait for the processing of the message
            Thread.sleep( 1000 );

            if( classifier_ipc.getProcess().isAlive() )
            {
                force = true;
            }
        }
        if( force )
        {
            if( classifier_ipc.getProcess().isAlive() )
            {
                classifier_ipc.getProcess().destroy();
            }
        }
        classifier_ipc = null;
    }
}
