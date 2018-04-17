package AudioDataSource;

import AudioDataSource.ADCache.CachedAudioDataSource;
import AudioDataSource.Exceptions.DataSourceException;
import AudioDataSource.Exceptions.DataSourceExceptionCause;
import AudioDataSource.FileADS.FileAudioSourceFactory;
import AudioDataSource.FileADS.IFileAudioDataSource;
import ProjectStatics.ProjectStatics;
import Utils.MyPair;
import javafx.util.Pair;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by Alex on 13.03.2018.
 */
public class Cached_ADS_Manager
{
    private static HashMap< String, MyPair< CachedAudioDataSource, Integer > > map = new HashMap<>();
    private static HashMap< CachedAudioDataSource, Long > to_be_closed = new HashMap<>();
    private static final long close_delay_ms = 1000;

    public static CachedAudioDataSource get_cache( String filePath ) throws DataSourceException
    {
        MyPair< CachedAudioDataSource, Integer > pair = map.get( filePath );
        if( pair == null )
        {
            IFileAudioDataSource ifads = FileAudioSourceFactory.fromFile( filePath );
            CachedAudioDataSource cads = new CachedAudioDataSource( ifads, ProjectStatics.getDefault_cache_size(), ProjectStatics.getDefault_cache_page_size() );
            pair = new MyPair< CachedAudioDataSource, Integer >( cads, 1 );
            map.put( filePath, pair );
        }
        else
        {
            pair.setRight( pair.getRight() + 1 );
        }
        process_to_be_closed();
        return pair.getLeft();
    }

    public static void finish_all_caches() throws DataSourceException
    {
        for( MyPair< CachedAudioDataSource, Integer > pair : map.values() )
        {
            pair.getLeft().flushAll();
            pair.getLeft().close();
        }
        map.clear();
        for( CachedAudioDataSource cads : to_be_closed.keySet() )
        {
            cads.flushAll();
            cads.close();
        }
        to_be_closed.clear();
    }

    public static void mark_use( String filePath )
    {
        MyPair< CachedAudioDataSource, Integer > pair = map.get( filePath );
        if( pair != null )
        {
            pair.setRight( pair.getRight() + 1 );
            to_be_closed.remove( pair.getLeft() );
        }
        try
        {
            process_to_be_closed();
        }
        catch( DataSourceException e )
        {
            e.printStackTrace();
        }
    }

    public static void release_use( String filePath ) throws DataSourceException
    {
        MyPair< CachedAudioDataSource, Integer > pair = map.get( filePath );
        if( pair != null )
        {
            pair.setRight( pair.getRight() - 1 );
            if( pair.getRight() == 0 )
            {
                to_be_closed.put( pair.getLeft(), System.currentTimeMillis() );
                map.remove( filePath );
            }
            if( pair.getRight() < 0 )
            {
                throw new DataSourceException( "Tried to release already released cache", DataSourceExceptionCause.THIS_SHOULD_NEVER_HAPPEN );
            }
        }
        process_to_be_closed();
    }

    private static void process_to_be_closed() throws DataSourceException
    {
        long time_now = System.currentTimeMillis();
        for( Map.Entry< CachedAudioDataSource, Long > entry : to_be_closed.entrySet() )
        {
            if( entry.getValue() + close_delay_ms < time_now )
            {
                entry.getKey().flushAll();
                entry.getKey().close();
                to_be_closed.remove( entry.getKey() );
            }
        }
    }

}
