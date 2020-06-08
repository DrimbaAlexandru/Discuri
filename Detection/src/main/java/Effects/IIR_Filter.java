package Effects;

import AudioDataSource.AudioSamplesWindow;
import Utils.Exceptions.DataSourceException;
import Utils.Exceptions.DataSourceExceptionCause;
import AudioDataSource.IAudioDataSource;
import SignalProcessing.Filters.FIR;
import SignalProcessing.Filters.IIR;
import Utils.DataTypes.Interval;

/**
 * Created by Alex on 11.01.2018.
 */
public class IIR_Filter implements IEffect
{
    private IIR filter = null;
    private int max_chunk_size = 1024;
    private final static float[] identity_FIR_coeffs = { 1 };
    private float progress = 0;

    @Override
    public void apply( IAudioDataSource dataSource, IAudioDataSource dataDest, Interval interval ) throws DataSourceException
    {
        if( filter == null )
        {
            throw new DataSourceException( "No filter was set.", DataSourceExceptionCause.INVALID_STATE );
        }

        final int max_filter_len = Math.max( filter.getFb_coeff_nr() - 1, filter.getFf_coeff_nr() );

        if( max_filter_len + filter.getFf_coeff_nr() > max_chunk_size )
        {
            throw new DataSourceException( "Chunk size must be at least the sum of the maximum filter size (FIR and IIR) and the FIR filter size", DataSourceExceptionCause.INVALID_STATE );
        }

        final boolean isInPlace = ( dataDest == dataSource );
        int buf1_size = filter.getFf_coeff_nr();
        int buf2_size = filter.getFb_coeff_nr() - 1;
        float[][] prev_left_samples = null;
        float[][] regression_buffer = new float[ dataSource.get_channel_number() ][ buf2_size ];
        if( isInPlace )
        {
            prev_left_samples = new float[ dataSource.get_channel_number() ][ buf1_size ];
        }

        int temp_len;
        int i, j, k;
        int first_needed_sample_index;
        int first_fetchable_sample_index;

        AudioSamplesWindow win;
        Interval applying_range = new Interval( 0, 0 );

        interval.r = Math.min( dataSource.get_sample_number(), interval.r );
        interval.l = Math.max( 0, interval.l );

        i = interval.l;
        progress = 0;

        first_needed_sample_index = i - max_filter_len;
        first_fetchable_sample_index = Math.max( 0, first_needed_sample_index );
        temp_len = Math.min( interval.r - first_fetchable_sample_index, max_chunk_size );
        win = dataSource.get_samples( first_fetchable_sample_index, temp_len );
        temp_len = win.get_length();
        applying_range.l = i - first_fetchable_sample_index;
        applying_range.r = temp_len;
        if( applying_range.get_length() == 0 )
        {
            throw new DataSourceException( "Avoided infinite loop!", DataSourceExceptionCause.INVALID_STATE );
        }

        for( k = 0; k < dataSource.get_channel_number(); k++ )
        {
            if( isInPlace )
            {
                for( j = 0; j < buf1_size; j++ )
                {
                    prev_left_samples[ k ][ buf1_size - 1 - j ] = win.getSamples()[ k ][ temp_len - 1 - j ];
                }
            }

            filter.apply( win.getSamples()[ k ], applying_range, true );
            win.markModified();

            for( j = 0; j < buf2_size; j++ )
            {
                regression_buffer[ k ][ buf2_size - 1 - j ] = win.getSamples()[ k ][ temp_len - 1 - j ];
            }
        }
        dataDest.put_samples( win );
        i += applying_range.get_length();
        progress = 1.0f * ( i - interval.l ) / interval.get_length();

        for( ; i < interval.r; )
        {
            first_needed_sample_index = i - max_filter_len;
            first_fetchable_sample_index = Math.max( 0, first_needed_sample_index );
            temp_len = Math.min( interval.r - first_fetchable_sample_index, max_chunk_size );
            win = dataSource.get_samples( first_fetchable_sample_index, temp_len );
            temp_len = win.get_length();
            applying_range.l = i - first_fetchable_sample_index;
            applying_range.r = temp_len;
            if( applying_range.get_length() == 0 )
            {
                throw new DataSourceException( "Avoided infinite loop!", DataSourceExceptionCause.INVALID_STATE );
            }

            for( k = 0; k < dataSource.get_channel_number(); k++ )
            {
                if( isInPlace )
                {
                    for( j = 0; j < buf1_size; j++ )
                    {
                        win.putSample( first_fetchable_sample_index + buf1_size - 1 - j, k, prev_left_samples[ k ][ buf1_size - 1 - j ] );
                        prev_left_samples[ k ][ buf1_size - 1 - j ] = win.getSamples()[ k ][ temp_len - 1 - j ];
                    }
                }

                FIR fir = new FIR( filter.getFf(), filter.getFf_coeff_nr() );
                fir.apply( win.getSamples()[ k ], applying_range );

                for( j = 0; j < buf1_size; j++ )
                {
                    win.putSample( first_fetchable_sample_index + buf1_size - 1 - j, k, regression_buffer[ k ][ buf1_size - 1 - j ] );
                }
                IIR iir = new IIR( identity_FIR_coeffs, 1, filter.getFb(), filter.getFb_coeff_nr() );
                iir.apply( win.getSamples()[ k ], applying_range, true );
                win.markModified();

                for( j = 0; j < buf2_size; j++ )
                {
                    regression_buffer[ k ][ buf2_size - 1 - j ] = win.getSamples()[ k ][ temp_len - 1 - j ];
                }
            }
            dataDest.put_samples( win );
            i += applying_range.get_length();
            progress = 1.0f * ( i - interval.l ) / interval.get_length();
        }
    }

    @Override
    public float getProgress()
    {
        return progress;
    }

    public void setFilter( IIR filter )
    {
        this.filter = filter;
    }

    public void setMax_chunk_size( int max_chunk_size )
    {
        this.max_chunk_size = max_chunk_size;
    }
}
