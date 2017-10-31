package WavFile;

import WavFile.BasicWavFile.BasicWavFile;
import WavFile.BasicWavFile.BasicWavFileException;
import WavFile.WavCache.WavCache;
import WavFile.WavCache.WavCacheError;
import WavFile.WavCache.WavCachedWindow;

import java.io.File;
import java.io.IOException;

/**
 * Created by Alex on 08.09.2017.
 */
public class WavFile
{

    private BasicWavFile wavFile;
    private WavCache cache;

    public WavFile( String filepath, long cached_samples_number ) throws IOException, BasicWavFileException
    {
        wavFile = BasicWavFile.openWavFile( new File( filepath ) );
        cache = new WavCache( cached_samples_number * wavFile.getNumChannels() );
    }

    public void put_samples( WavCachedWindow samples ) throws IOException, BasicWavFileException
    {
        int i, j;
        int k;
        WavCachedWindow win;
        double localBuffer[][];
        int localSize;
        boolean needs_free;

        if( samples.getSample_number() > cache.getMaxCacheSize() )
        {
            throw new BasicWavFileException( "The requested amount of data cannot fit the cache." );
        }

        for( i = samples.getFirst_sample_index(); i < samples.getFirst_sample_index() + samples.getSample_number(); i++ )
        {
            win = cache.getCacheWindow( i );
            if( win == null )
            {
                localSize = Math.min( cache.getNextCachedSampleIndex( i ) - i, samples.getFirst_sample_index() + samples.getSample_number() - i );
                localSize = ( localSize < 0 ? samples.getFirst_sample_index() - i + samples.getSample_number() : localSize );
                localBuffer = new double[ localSize ][ samples.getChannel_number() ];
                for( k = 0; k < samples.getChannel_number(); k++ )
                {
                    for( j = 0; j < localSize; j++ )
                    {
                        localBuffer[ j ][ k ] = samples.getSample( i + j, k );
                    }
                }

                do
                {
                    needs_free = false;
                    try
                    {
                        cache.newCache( localBuffer, i, localSize, samples.getChannel_number() );
                    }
                    catch( BasicWavFileException e )
                    {
                        if( cache.getLastErr() == WavCacheError.NOT_ENOUGH_FREE_SPACE )
                        {
                            System.out.println( "Need to free cache" );
                        }
                        else
                        {
                            throw e;
                        }
                    }
                    if( cache.getLastErr() == WavCacheError.NOT_ENOUGH_FREE_SPACE )
                    {
                        needs_free = true;
                        if( !cache.freeOldestCache() )
                        {
                            write_samples( cache.getOldestUsedCache() );
                            cache.getOldestUsedCache().markAsFlushed();
                            cache.freeOldestCache();
                        }
                    }
                }
                while( needs_free );
                /* This one came out strange. If you try to read more than the cache size, but a part of what you need to read is already cached, and the remaining data to be cached would fit an empty cache, the cache is flushed and the new data is cached. Both parts (the previously cached samples and the newly cached samples) are returned on the output buffer.
                * */
                i += localSize - 1;
            }
            else
            {
                for( k = 0; k < samples.getChannel_number(); k++ )
                {
                    win.putSample( i, k, samples.getSample( i, k ) );
                }
            }
        }
    }

    public WavCachedWindow get_samples( int first_sample_index, int sample_number ) throws IOException, BasicWavFileException
    {
        double samples[][] = new double[ sample_number ][ wavFile.getNumChannels() ];
        double temp_buffer[][];
        int i, j, k;
        int temp_length;
        WavCachedWindow win;
        if( sample_number > cache.getMaxCacheSize() )
        {
            throw new BasicWavFileException( "The requested amount of data cannot fit the cache. Use get_compact_samples() or use a smaller window size" );
        }

        for( i = first_sample_index; i < first_sample_index + sample_number; i++ )
        {
            win = cache.getCacheWindow( i );
            if( win == null )
            {
                temp_length = Math.min( cache.getNextCachedSampleIndex( i ) - i, first_sample_index + sample_number - i );
                temp_length = ( temp_length < 0 ? first_sample_index - i + sample_number : temp_length );
                temp_buffer = new double[ temp_length ][ wavFile.getNumChannels() ];
                read_samples( i, temp_buffer, temp_length );

                win = new WavCachedWindow( temp_buffer, i, temp_length, wavFile.getNumChannels() );
                put_samples( win );
                if( temp_length == sample_number )
                {
                    return win;
                }

                for( j = 0; j < temp_length; j++ )
                {
                    for( k = 0; k < wavFile.getNumChannels(); k++ )
                    {
                        samples[ i - first_sample_index + j ][ k ] = temp_buffer[ j ][ k ];
                    }
                }

                i += temp_length - 1;
            }
            else
            {
                temp_length = Math.min( sample_number - i + first_sample_index,
                                        win.getSample_number() - i + win.getFirst_sample_index() );
                for( j = 0; j < temp_length; j++ )
                {
                    for( k = 0; k < wavFile.getNumChannels(); k++ )
                    {
                        samples[ i - first_sample_index + j ][ k ] = win.getSample( i + j, k );
                    }
                }

                i += temp_length - 1;
            }
        }
        return new WavCachedWindow( samples, first_sample_index, sample_number, wavFile.getNumChannels() );
    }

    private void read_samples( int first_sample_index, double buffer[][], int sample_number ) throws IOException, BasicWavFileException
    {
        if( sample_number > cache.getMaxCacheSize() )
        {
            throw new BasicWavFileException( "The requested amount of data cannot fit the cache." );
        }
        wavFile.set_stream_position( first_sample_index, 0 );
        wavFile.readFrames( buffer, sample_number );
    }

    public WavCachedWindow get_compact_samples( int first_sample_index, int sample_number, int resulted_sample_number, boolean alternate_min_max ) throws IOException, BasicWavFileException
    {
        int i, j, previ, curi;
        boolean alt;
        double samples[][] = new double[ resulted_sample_number ][ getChannelsNumber() ];
        WavCachedWindow win;

        previ = -1;
        alt = false;

        if( sample_number <= resulted_sample_number )
        {
            win = get_samples( first_sample_index, sample_number );

            for( curi = 0; curi < resulted_sample_number; curi++ )
            {
                for( j = 0; j < getChannelsNumber(); j++ )
                {
                    samples[ curi ][ j ] = win.getSample( first_sample_index + curi * sample_number / resulted_sample_number, j );
                }
            }
        }
        else
        {
            if( sample_number * getChannelsNumber() <= cache.getMaxCacheSize() )
            {
                win = get_samples( first_sample_index, sample_number );

                for( i = 0; i < sample_number; i++ )
                {
                    curi = i * resulted_sample_number / sample_number;

                    if( previ != curi )
                    {
                        previ = curi;
                        for( j = 0; j < getChannelsNumber(); j++ )
                        {
                            samples[ curi ][ j ] = win.getSample( first_sample_index + i, j );
                        }
                    }
                    else
                    {
                        for( j = 0; j < getChannelsNumber(); j++ )
                        {
                            if( alt )
                            {
                                samples[ curi ][ j ] = Math.min( win.getSample( first_sample_index + i, j ), samples[ curi ][ j ] );
                            }
                            else
                            {
                                samples[ curi ][ j ] = Math.max( win.getSample( first_sample_index + i, j ), samples[ curi ][ j ] );
                            }
                        }
                    }
                    alt = !alt;
                }
            }

        }
        return new WavCachedWindow( samples, 0, resulted_sample_number, getChannelsNumber() );
    }

    private void write_samples( WavCachedWindow win ) throws IOException, BasicWavFileException
    {
        wavFile.set_stream_position( win.getFirst_sample_index(), 0 );
        wavFile.writeFrames( win.getSamples(), win.getSample_number() );
    }

    public void flush() throws IOException, BasicWavFileException
    {
        for( WavCachedWindow win : cache.getCaches().values() )
        {
            if( win.isModified() )
            {
                write_samples( win );
                win.markAsFlushed();
            }
        }
    }

    public long getSampleNumber()
    {
        return wavFile.getNumFrames();
    }

    public int getChannelsNumber()
    {
        return wavFile.getNumChannels();
    }

    public int getSampleRate()
    {
        return ( int )wavFile.getSampleRate();
    }

}
