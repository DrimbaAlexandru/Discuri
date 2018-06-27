package SignalProcessing.Effects;

import AudioDataSource.AudioSamplesWindow;
import AudioDataSource.IAudioDataSource;
import Exceptions.DataSourceException;
import Exceptions.DataSourceExceptionCause;
import Utils.Interval;
import Utils.Stylus;

/**
 * Created by Alex on 26.06.2018.
 */
public class Groove_Retracking implements IEffect
{
    private double progress = 0;
    private double stylus_width = 1;//in micrometers
    private double stylus_length = 1;//in micrometers
    private double groove_max_ampl_um = 80;
    private int chunk_size = 96000;

    @Override
    public void apply( IAudioDataSource dataSource, IAudioDataSource dataDest, Interval interval ) throws DataSourceException
    {
        int i, temp_len;
        Stylus stylus;
        AudioSamplesWindow win;
        double[][] flush_array = new double[ dataDest.get_channel_number() ][ chunk_size ];

        if( dataSource == dataDest )
        {
            throw new DataSourceException( "Data source and data destination cannot be the same!", DataSourceExceptionCause.INVALID_PARAMETER );
        }
        interval.limit( 0, dataSource.get_sample_number() );

        i = interval.l;
        for( ; i < interval.r; )
        {
            stylus = new Stylus( stylus_width / groove_max_ampl_um, stylus_length, 33 + 1.0 / 3, 3 * 25.4, dataSource.get_sample_rate() );
            temp_len = Math.min( chunk_size, interval.r - i );
            win = dataSource.get_samples( i - stylus.getSide_length(), temp_len + stylus.getSide_length() * 2 );
            for( int k = 0; k < Math.min( 2, win.get_channel_number() ); k++ )
            {
                for( int j = i; j < i + temp_len; j++ )
                {
                    flush_array[ k ][ j - i ] = win.getSamples()[ k ][ j - win.get_first_sample_index() ] + stylus.get_offset( win.getSamples()[ k ], j - win.get_first_sample_index(), k == 1 );
                }
            }
            AudioSamplesWindow flush = new AudioSamplesWindow( flush_array, i, temp_len, win.get_channel_number() );
            dataDest.put_samples( flush );
            i += temp_len;
            progress = 1.0 * ( i - interval.l ) / interval.get_length();
        }
    }

    @Override
    public double getProgress()
    {
        return progress;
    }

    public void setStylus_length( double stylus_length )
    {
        this.stylus_length = stylus_length;
    }

    public void setStylus_width( double stylus_width )
    {
        this.stylus_width = stylus_width;
    }
}
