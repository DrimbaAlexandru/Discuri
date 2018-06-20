package AudioDataSource;

import AudioDataSource.CachedADS.CachedAudioDataSource;
import Exceptions.DataSourceException;
import Exceptions.DataSourceExceptionCause;
import AudioDataSource.FileADS.FileAudioSourceFactory;
import AudioDataSource.FileADS.IFileAudioDataSource;
import ProjectManager.ProjectStatics;
import Utils.MyPair;

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
    private static HashMap< String, MyPair< CachedAudioDataSource, Long > > to_be_closed = new HashMap<>();
    private static final long close_delay_ms = 5000;

    public static CachedAudioDataSource get_cache( String filePath ) throws DataSourceException
    {
        MyPair< CachedAudioDataSource, Integer > map_pair = map.get( filePath );
        MyPair< CachedAudioDataSource, Long > from_close_list = to_be_closed.get( filePath );

        if( from_close_list != null )
        {
            if( map_pair != null )
            {
                throw new DataSourceException( DataSourceExceptionCause.THIS_SHOULD_NEVER_HAPPEN );
            }
            to_be_closed.remove( filePath );
            map.put( filePath, new MyPair<>( from_close_list.getLeft(), 1 ) );
            map_pair = map.get( filePath );
        }
        else
        {
            if( map_pair == null )
            {
                IFileAudioDataSource ifads = FileAudioSourceFactory.fromFile( filePath );
                CachedAudioDataSource cads = new CachedAudioDataSource( ifads, ProjectStatics.getDefault_cache_size(), ProjectStatics.getDefault_cache_page_size() );
                map_pair = new MyPair< CachedAudioDataSource, Integer >( cads, 1 );
                map.put( filePath, map_pair );
            }
            else
            {
                map_pair.setRight( map_pair.getRight() + 1 );
            }
        }
        process_to_be_closed();
        return map_pair.getLeft();
    }

    public static void finish_all_caches() throws DataSourceException
    {
        for( MyPair< CachedAudioDataSource, Integer > pair : map.values() )
        {
            pair.getLeft().flushAll();
            pair.getLeft().close();
        }
        map.clear();
        for( MyPair< CachedAudioDataSource, Long > pairs : to_be_closed.values() )
        {
            pairs.getLeft().flushAll();
            pairs.getLeft().close();
        }
        to_be_closed.clear();
    }

    public static void mark_use( String filePath )// throws DataSourceException
    {
        MyPair< CachedAudioDataSource, Integer > pair = map.get( filePath );

        if( pair != null )
        {
            pair.setRight( pair.getRight() + 1 );
        }
        else
        {
            //throw new DataSourceException( "Mark_use can be used only for files that already have their cache referenced at least once.", DataSourceExceptionCause.INVALID_PARAMETER );
        }

        //process_to_be_closed();

    }

    public static void release_use( String filePath ) throws DataSourceException
    {
        MyPair< CachedAudioDataSource, Integer > pair = map.get( filePath );
        if( pair != null )
        {
            pair.setRight( pair.getRight() - 1 );
            if( pair.getRight() == 0 )
            {
                to_be_closed.put( filePath, new MyPair<>( pair.getLeft(), System.currentTimeMillis() ) );
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
        List< String > to_be_removed = new ArrayList<>();
        for( Map.Entry< String, MyPair< CachedAudioDataSource, Long > > entry : to_be_closed.entrySet() )
        {
            if( entry.getValue().getRight() + close_delay_ms < time_now )
            {
                to_be_removed.add( entry.getKey() );
            }
        }
        for( String files : to_be_removed )
        {
            to_be_closed.get( files ).getLeft().flushAll();
            to_be_closed.get( files ).getLeft().close();
            to_be_closed.remove( files );
        }
    }

    public static void instant_release_file( String filePath ) throws DataSourceException
    {
        if( map.containsKey( filePath ) )
        {
            throw new DataSourceException( "File cache is still in use" );
        }
        if( to_be_closed.containsKey( filePath ) )
        {
            MyPair< CachedAudioDataSource, Long > pair = to_be_closed.get( filePath );
            pair.getLeft().flushAll();
            pair.getLeft().close();
            to_be_closed.remove( filePath );
        }
    }

}
