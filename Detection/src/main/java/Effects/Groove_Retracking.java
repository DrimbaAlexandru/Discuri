package Effects;

import AudioDataSource.AudioSamplesWindow;
import AudioDataSource.IAudioDataSource;
import Utils.Exceptions.DataSourceException;
import Utils.Exceptions.DataSourceExceptionCause;
import SignalProcessing.FunctionApproximation.FourierInterpolator;
import SignalProcessing.Windowing.Windowing;
import Utils.DataTypes.Interval;
import Utils.Stylus;
import Utils.Util_Stuff;

/**
 * Created by Alex on 26.06.2018.
 */
public class Groove_Retracking implements IEffect
{
    private float progress = 0;
    private float stylus_width = 1;//in micrometers
    private float stylus_length = 1;//in micrometers
    private float groove_max_ampl_um = 100;
    private int chunk_size = 64;
    private int drop_size = 8;
    private int resample_rate_factor = 1;
    private int stylus_rebuild_period = 10;
    private boolean upside_down = false;

    public void setUpside_down( boolean upside_down )
    {
        this.upside_down = upside_down;
    }

    public void setChunk_size( int chunk_size )
    {
        this.chunk_size = chunk_size;
    }

    public void setGroove_max_ampl_um( float groove_max_ampl_um )
    {
        this.groove_max_ampl_um = groove_max_ampl_um;
    }

    public void setResample_rate_factor( int resample_rate_factor )
    {
        this.resample_rate_factor = resample_rate_factor;
    }

    public void setDrop_size( int drop_size )
    {
        this.drop_size = drop_size;
    }

    @Override
    public void apply( IAudioDataSource dataSource, IAudioDataSource dataDest, Interval interval ) throws DataSourceException
    {
        int i;
        Stylus stylus = null;
        AudioSamplesWindow win;
        FourierInterpolator interpolator = new FourierInterpolator();

        int sample_last_stylus_built = 0;
        int first_usable_resampled_sample, last_usable_resampled_sample;

        chunk_size = Util_Stuff.next_power_of_two( chunk_size - 1 );

        float[] work_array = new float[ chunk_size * resample_rate_factor ];
        float[][] flush_array = new float[ dataDest.get_channel_number() ][ chunk_size - drop_size * 2 ];

        if( dataSource == dataDest )
        {
            throw new DataSourceException( "Data source and data destination cannot be the same!", DataSourceExceptionCause.INVALID_PARAMETER );
        }

        //Build the stylus for the interval's end to get the usable samples count
        stylus = new Stylus( stylus_width / groove_max_ampl_um, stylus_length, 33 + 1.0f / 3, 2.6563f * 25.4f, dataSource.get_sample_rate() * resample_rate_factor );
        interval.limit( interval.l, dataSource.get_sample_number() - drop_size - (stylus.getSide_length() + resample_rate_factor - 1 ) / resample_rate_factor );

        sample_last_stylus_built = interval.l;
        stylus = new Stylus( stylus_width / groove_max_ampl_um, stylus_length, 33 + 1.0f / 3, 3 * 25.4f, dataSource.get_sample_rate() * resample_rate_factor );

        first_usable_resampled_sample = ( drop_size * resample_rate_factor + stylus.getSide_length() + resample_rate_factor - 1 ) / resample_rate_factor * resample_rate_factor;
        last_usable_resampled_sample = ( chunk_size * resample_rate_factor - drop_size*resample_rate_factor - stylus.getSide_length() - 1 ) / resample_rate_factor * resample_rate_factor;
        if( last_usable_resampled_sample < first_usable_resampled_sample )
        {
            throw new DataSourceException( "0 usable samples", DataSourceExceptionCause.INVALID_STATE );
        }

        interval.limit( drop_size + ( stylus.getSide_length() + resample_rate_factor - 1 ) / resample_rate_factor, interval.r );

        i = interval.l;
        for( ; i < interval.r; )
        {
            if( i - sample_last_stylus_built >= stylus_rebuild_period * dataSource.get_sample_rate() )
            {
                sample_last_stylus_built = i;
                stylus = new Stylus( stylus_width / groove_max_ampl_um, stylus_length, 33 + 1.0f / 3, 3 * 25.4f, dataSource.get_sample_rate() * resample_rate_factor );

                first_usable_resampled_sample = ( drop_size * resample_rate_factor + stylus.getSide_length() + resample_rate_factor - 1 ) / resample_rate_factor * resample_rate_factor;
                last_usable_resampled_sample = ( chunk_size * resample_rate_factor - drop_size*resample_rate_factor - stylus.getSide_length() - 1 ) / resample_rate_factor * resample_rate_factor;
                if( last_usable_resampled_sample < first_usable_resampled_sample )
                {
                    throw new DataSourceException( "0 usable samples", DataSourceExceptionCause.INVALID_STATE );
                }
            }

            win = dataSource.get_samples( i - drop_size - ( stylus.getSide_length() + resample_rate_factor - 1 ) / resample_rate_factor, chunk_size );
            if( win.get_length() != chunk_size )
            {
                throw new DataSourceException( "Assertion failed. Window length less than expected", DataSourceExceptionCause.THIS_SHOULD_NEVER_HAPPEN );
            }

            for( int k = 0; k < Math.min( 2, win.get_channel_number() ); k++ )
            {
                Windowing.apply( win.getSamples()[ k ], 0, drop_size, Windowing.inv_cos_sq_window );
                Windowing.apply( win.getSamples()[ k ], chunk_size - drop_size, drop_size, Windowing.cos_sq_window );
                interpolator.prepare( win.getSamples()[ k ], chunk_size );
                interpolator.get_values( work_array, chunk_size * resample_rate_factor );

                for( int j = first_usable_resampled_sample; j <= last_usable_resampled_sample; j += resample_rate_factor )
                {
                    flush_array[ k ][ ( j - first_usable_resampled_sample ) / resample_rate_factor ] = work_array[ j ] + stylus.get_offset( work_array, j, ( k == 0 ) ^ upside_down );
                }
            }
            AudioSamplesWindow flush = new AudioSamplesWindow( flush_array, i, ( last_usable_resampled_sample - first_usable_resampled_sample ) / resample_rate_factor + 1, win.get_channel_number() );
            dataDest.put_samples( flush );
            i += flush.get_length();
            progress = 1.0f * ( i - interval.l ) / interval.get_length();
            System.out.println( progress * 100 );
            if( interval.r - i < chunk_size )
            {
                i = interval.r;
                progress = 1.0f * ( i - interval.l ) / interval.get_length();
            }
        }
    }

    @Override
    public float getProgress()
    {
        return progress;
    }

    public void setStylus_length( float stylus_length )
    {
        this.stylus_length = stylus_length;
    }

    public void setStylus_width( float stylus_width )
    {
        this.stylus_width = stylus_width;
    }
}
