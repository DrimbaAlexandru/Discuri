package ProjectManager;

import AudioDataSource.CachedADS.CachedAudioDataSource;
import AudioDataSource.FileADS.FileAudioSourceFactory;
import AudioDataSource.FileADS.IFileAudioDataSource;
import AudioDataSource.VersionedADS.AudioDataSourceVersion;
import AudioDataSource.VersionedADS.VersionedAudioDataSource;
import Utils.DataTypes.EffectType;
import Utils.Exceptions.DataSourceException;
import Utils.Exceptions.DataSourceExceptionCause;
import Utils.DataStructures.MarkerFile.MarkerFile;
import Utils.DataStructures.MarkerFile.Marking;
import Effects.IEffect;
import Effects.SampleClassifier.AIDamageRecognition;
import Effects.SampleClassifier.RemoteAIServer.IOP_IPC_stdio;
import Effects.SampleClassifier.RemoteAIServer.IOP_msg_struct_type;
import Utils.DataTypes.Interval;

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
    private static CachedAudioDataSource currentAudioCache = null;
    private static CachedAudioDataSource prevAudioCache = null;

    private static MarkerFile markerFile = new MarkerFile();

    private static CachedAudioDataSource damageCache = null;
    private static AudioDataSourceVersion damageADS = null;

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
            currentAudioCache.flushAll();
            versionedADS.dispose();
        }
        if( damageCache != null )
        {
            damageADS.close();
            damageCache.close();

            damageADS = null;
            damageCache = null;
        }

        versionedADS = new VersionedAudioDataSource( filepath );

        if( currentAudioCache == null )
        {
            currentAudioCache = new CachedAudioDataSource( versionedADS.get_current_version(), ProjectStatics.getProject_cache_size(), ProjectStatics.getProject_cache_page_size() );
        }
        else
        {
            currentAudioCache.setDataSource( versionedADS.get_current_version() );
        }
        prevAudioCache = null;

        damageADS = new AudioDataSourceVersion( currentAudioCache.get_sample_rate(), currentAudioCache.get_channel_number(), currentAudioCache.get_sample_number() );
        damageADS.setAllow_unmapped_get( true );
        damageADS.setByte_depth( ( byte )2 );
        damageCache = new CachedAudioDataSource( damageADS, ProjectStatics.get_temp_file_max_samples(), ProjectStatics.getProject_cache_page_size() );
    }

    private static void create_next_version() throws DataSourceException
    {
        check_thread_access();
        if( versionedADS == null )
        {
            throw new DataSourceException( "Current project is empty", DataSourceExceptionCause.INVALID_STATE );
        }

        currentAudioCache.flushAll();
        prevAudioCache = currentAudioCache;

        AudioDataSourceVersion newVersion = versionedADS.create_next_version();
        currentAudioCache = new CachedAudioDataSource( newVersion, ProjectStatics.getProject_cache_size(), ProjectStatics.getProject_cache_page_size() );
    }

    public static void apply_effect( IEffect effect, Interval interval ) throws DataSourceException
    {
        check_thread_access();
        if( versionedADS == null )
        {
            throw new DataSourceException( "Current project is empty", DataSourceExceptionCause.INVALID_STATE );
        }

        switch( effect.getEffectType() )
        {
            case NORMAL_AUDIO_EFFECT:
                create_next_version();
                effect.apply( prevAudioCache, currentAudioCache, interval );
                break;

            case READ_ONLY_AUDIO_EFFECT:
                effect.apply( currentAudioCache, null, interval );
                break;

            case DAMAGE_GENERATION_EFFECT:
                effect.apply( currentAudioCache, damageCache, interval );
                break;

            case DAMAGE_READ_EFFECT:
//                IFileAudioDataSource dest = FileAudioSourceFactory.createFile( "C:\\Users\\Alex\\Desktop\\damage_mvg_avg.wav", damageCache.get_channel_number(), damageCache.get_sample_rate(), 2 );
//                CachedAudioDataSource dest_cache = new CachedAudioDataSource( dest, ProjectStatics.getProject_cache_size(), ProjectStatics.getProject_cache_page_size() );
                effect.apply( damageCache, null, interval );
//                dest_cache.flushAll();
//                dest_cache.close();
//                dest.close();
                break;
        }
    }

    public static void undo() throws DataSourceException
    {
        check_thread_access();
        if( versionedADS == null )
        {
            throw new DataSourceException( "Current project is empty", DataSourceExceptionCause.INVALID_STATE );
        }
        versionedADS.undo();
        currentAudioCache.setDataSource( versionedADS.get_current_version() );
    }

    public static void redo() throws DataSourceException
    {
        check_thread_access();
        if( versionedADS == null )
        {
            throw new DataSourceException( "Current project is empty", DataSourceExceptionCause.INVALID_STATE );
        }
        versionedADS.redo();
        currentAudioCache.setDataSource( versionedADS.get_current_version() );
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
            currentAudioCache.flushAll();
            versionedADS.dispose();
        }
        if( damageADS != null )
        {
            damageCache.flushAll();
            damageADS.close();
        }
    }

    public static CachedAudioDataSource getCurrentAudioCache() throws DataSourceException
    {
        return currentAudioCache;
    }

    public static CachedAudioDataSource getDamageCache() throws DataSourceException
    {
        return damageCache;
    }

    public static MarkerFile getMarkerFile() throws DataSourceException
    {
        return markerFile;
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
