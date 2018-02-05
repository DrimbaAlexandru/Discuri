package SignalProcessing.Effects;

import AudioDataSource.ADCache.AudioSamplesWindow;
import AudioDataSource.Exceptions.DataSourceException;
import AudioDataSource.Exceptions.DataSourceExceptionCause;
import AudioDataSource.IAudioDataSource;
import ProjectStatics.ProjectStatics;
import SignalProcessing.Filters.FIR;
import SignalProcessing.Filters.IIR;
import Utils.Interval;

/**
 * Created by Alex on 11.01.2018.
 */
public class IIR_Filter implements IEffect
{
    private IIR filter = null;
    private final static int max_chunk_size = 8;
    private final static double[] identity_FIR_coeffs = { 1 };
    private final static double[] identity_IIR_coeffs = { 1 };

    @Override
    public String getName()
    {
        return "IIR Filter";
    }

    @Override
    public void apply( IAudioDataSource dataSource, IAudioDataSource dataDest, Interval interval ) throws DataSourceException
    {
        if( filter == null )
        {
            throw new DataSourceException( "No filter was set.", DataSourceExceptionCause.INVALID_STATE );
        }
        if( filter.getFf_coeff_nr() >= max_chunk_size || filter.getFb_coeff_nr() >= max_chunk_size )
        {
            throw new DataSourceException( "Buffer is smaller or equal to filter length", DataSourceExceptionCause.INVALID_STATE );
        }

        final int max_filter_len = Math.max( filter.getFb_coeff_nr() - 1, filter.getFf_coeff_nr() );
        double[][] prev_left_samples = new double[ dataSource.get_channel_number() ][ max_filter_len ];
        double[][] buffer = new double[ dataSource.get_channel_number() ][ max_filter_len ];
        double xchg;

        int temp_len;
        int i, j, k;
        int first_needed_sample_index;
        int first_fetchable_sample_index;
        int buf_len = 0;
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
        applying_range.r = temp_len;
        if( applying_range.get_length() == 0 )
        {
            throw new DataSourceException( "Avoided infinite loop!", DataSourceExceptionCause.INVALID_STATE );
        }

        for( k = 0; k < dataSource.get_channel_number(); k++ )
        {
            for( j = 0; j < max_filter_len; j++ )
            {
                prev_left_samples[ k ][ j ] = 0;
            }
            for( j = 0; j < filter.getFf_coeff_nr(); j++ )
            {
                prev_left_samples[ k ][ max_filter_len - 1 - j ] = win.getSamples()[ k ][ temp_len - 1 - j ];
            }
            filter.apply( win.getSamples()[ k ], applying_range, true );
            win.markModified();
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
            applying_range.r = temp_len;
            if( applying_range.get_length() == 0 )
            {
                throw new DataSourceException( "Avoided infinite loop!", DataSourceExceptionCause.INVALID_STATE );
            }

            for( k = 0; k < dataSource.get_channel_number(); k++ )
            {
                System.arraycopy( prev_left_samples[ k ], 0, buffer[ k ], 0, max_filter_len );

                for( j = 0; j < filter.getFf_coeff_nr(); j++ )
                {
                    prev_left_samples[ k ][ max_filter_len - 1 - j ] = win.getSamples()[ k ][ temp_len - 1 - j ];
                }
                buf_len = filter.getFf_coeff_nr() - ( first_fetchable_sample_index - first_needed_sample_index );
                for( j = 0; j < buf_len; j++ )
                {
                    xchg = win.getSample( first_fetchable_sample_index + buf_len - 1 - j, k );
                    win.putSample( first_fetchable_sample_index + buf_len - 1 - j, k, buffer[ k ][ max_filter_len - 1 - j ] );
                    buffer[ k ][ max_filter_len - 1 - j ] = xchg;
                }
                FIR fir = new FIR( filter.getFf(), filter.getFf_coeff_nr() );
                fir.apply( win.getSamples()[ k ], applying_range );

                for( j = 0; j < buf_len; j++ )
                {
                    win.putSample( first_fetchable_sample_index + buf_len - 1 - j, k, buffer[ k ][ max_filter_len - 1 - j ] );
                }
                IIR iir = new IIR( identity_FIR_coeffs, 1, filter.getFb(), filter.getFb_coeff_nr() );
                iir.apply( win.getSamples()[ k ], applying_range, true );

                win.markModified();
            }
            dataDest.put_samples( win );
            i += applying_range.get_length();
        }
    }

    public void setFilter( IIR filter )
    {
        this.filter = filter;
    }
}
