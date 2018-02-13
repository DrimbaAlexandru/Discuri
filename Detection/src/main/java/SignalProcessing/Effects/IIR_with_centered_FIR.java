package SignalProcessing.Effects;

import AudioDataSource.ADCache.AudioSamplesWindow;
import AudioDataSource.Exceptions.DataSourceException;
import AudioDataSource.Exceptions.DataSourceExceptionCause;
import AudioDataSource.IAudioDataSource;
import SignalProcessing.Filters.Equalizer_FIR;
import SignalProcessing.Filters.FIR;
import SignalProcessing.Filters.IIR;
import Utils.Interval;

/**
 * Created by Alex on 13.02.2018.
 */
public class IIR_with_centered_FIR implements IEffect
{
    private IIR iir_filter = null;
    private FIR fir_filter = null;
    private int max_chunk_size = 1024;
    private final static double[] identity_FIR_coeffs = { 1 };

    @Override
    public String getName()
    {
        return "Centered IIR Filter";
    }

    @Override
    public void apply( IAudioDataSource dataSource, IAudioDataSource dataDest, Interval interval ) throws DataSourceException
    {
        if( iir_filter == null )
        {
            throw new DataSourceException( "No filter was set.", DataSourceExceptionCause.INVALID_STATE );
        }

        final int max_filter_len = Math.max( iir_filter.getFb_coeff_nr() - 1, fir_filter.getFf_coeff_nr() / 2 );

        if( max_filter_len + iir_filter.getFf_coeff_nr() > max_chunk_size )
        {
            throw new DataSourceException( "Chunk size must be at least the sum of the maximum filter size (FIR and IIR) and half the FIR filter size", DataSourceExceptionCause.INVALID_STATE );
        }

        final boolean isInPlace = ( dataDest == dataSource );
        int buf1_size = fir_filter.getFf_coeff_nr() / 2;
        int buf2_size = max_filter_len;
        double[][] prev_left_samples = null;
        double[][] regression_buffer = new double[ dataSource.get_channel_number() ][ buf2_size ];
        final Equalizer_FIR equalizer_fir = new Equalizer_FIR( fir_filter );
        if( isInPlace )
        {
            prev_left_samples = new double[ dataSource.get_channel_number() ][ buf1_size ];
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

        first_needed_sample_index = i - max_filter_len;
        first_fetchable_sample_index = Math.max( 0, first_needed_sample_index );
        temp_len = Math.min( interval.r - first_fetchable_sample_index, max_chunk_size );
        win = dataSource.get_samples( first_fetchable_sample_index, temp_len );
        temp_len = win.get_length();
        applying_range.l = i - first_fetchable_sample_index;
        applying_range.r = temp_len - fir_filter.getFf_coeff_nr() / 2;
        if( applying_range.get_length() <= 0 )
        {
            throw new DataSourceException( "Avoided infinite loop!", DataSourceExceptionCause.INVALID_STATE );
        }

        for( k = 0; k < dataSource.get_channel_number(); k++ )
        {
            if( isInPlace )
            {
                for( j = 0; j < buf1_size; j++ )
                {
                    prev_left_samples[ k ][ buf1_size - 1 - j ] = win.getSamples()[ k ][ applying_range.r - 1 - j ];
                }
            }
            equalizer_fir.apply( win.getSamples()[ k ], applying_range );
            iir_filter.apply( win.getSamples()[ k ], applying_range, true );
            win.markModified();

            for( j = 0; j < buf2_size; j++ )
            {
                regression_buffer[ k ][ buf2_size - 1 - j ] = win.getSamples()[ k ][ applying_range.r - 1 - j ];
            }
        }
        dataDest.put_samples( win );
        i += applying_range.get_length();

        for( ; i < interval.r; )
        {
            first_needed_sample_index = i - max_filter_len;
            first_fetchable_sample_index = Math.max( 0, first_needed_sample_index );
            temp_len = Math.min( interval.r - first_fetchable_sample_index, max_chunk_size );
            win = dataSource.get_samples( first_fetchable_sample_index, temp_len );
            temp_len = win.get_length();
            applying_range.l = i - first_fetchable_sample_index;
            applying_range.r = temp_len - ( i >= dataSource.get_sample_number() - 1 - fir_filter.getFf_coeff_nr() / 2 ? 0 : fir_filter.getFf_coeff_nr() / 2 );
            if( applying_range.get_length() <= 0 )
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
                        prev_left_samples[ k ][ buf1_size - 1 - j ] = win.getSamples()[ k ][ applying_range.r - 1 - j ];
                    }
                }

                equalizer_fir.apply( win.getSamples()[ k ], applying_range );

                for( j = 0; j < buf2_size; j++ )
                {
                    win.putSample( first_fetchable_sample_index + buf1_size - 1 - j, k, regression_buffer[ k ][ buf2_size - 1 - j ] );
                }

                iir_filter.apply( win.getSamples()[ k ], applying_range, true );
                win.markModified();

                for( j = 0; j < buf2_size; j++ )
                {
                    regression_buffer[ k ][ buf2_size - 1 - j ] = win.getSamples()[ k ][ applying_range.r - 1 - j ];
                }
            }
            dataDest.put_samples( win );
            i += applying_range.get_length();
        }
    }

    public void setFilter( IIR filter )
    {
        this.iir_filter = new IIR( identity_FIR_coeffs, 1, filter.getFb(), filter.getFb_coeff_nr() );
        this.fir_filter = new FIR( filter.getFf(), filter.getFf_coeff_nr() );
    }

    public void setMax_chunk_size( int max_chunk_size )
    {
        this.max_chunk_size = max_chunk_size;
    }
}
