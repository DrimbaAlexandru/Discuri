package AudioDataSource;

import AudioDataSource.ADCache.CachedAudioDataSource;
import AudioDataSource.Exceptions.DataSourceException;
import AudioDataSource.FileADS.FileAudioSourceFactory;
import AudioDataSource.FileADS.IFileAudioDataSource;
import ProjectStatics.ProjectStatics;
import Utils.MyPair;

import java.util.HashMap;

/**
 * Created by Alex on 13.03.2018.
 */
public class Cached_ADS_Manager
{
    private static HashMap< String, MyPair< CachedAudioDataSource, Integer > > map = new HashMap<>();

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
        return pair.getLeft();
    }

    public static void flush_all_caches() throws DataSourceException
    {
        for( MyPair< CachedAudioDataSource, Integer > pair : map.values() )
        {
            pair.getLeft().flushAll();
        }
        map.clear();
    }

    public static void mark_use( String filePath )
    {
        MyPair< CachedAudioDataSource, Integer > pair = map.get( filePath );
        if( pair != null )
        {
            pair.setRight( pair.getRight() - 1 );
        }
    }

    public static void release_use( String filePath ) throws DataSourceException
    {
        MyPair< CachedAudioDataSource, Integer > pair = map.get( filePath );
        if( pair != null )
        {
            pair.setRight( pair.getRight() - 1 );
            if( pair.getRight() <= 0 )
            {
                pair.getLeft().flushAll();
                pair.getLeft().close();
                map.remove( filePath );
            }
        }

    }

}
