package SignalProcessing.Effects;

import AudioDataSource.ADCache.AudioSamplesWindow;
import AudioDataSource.Exceptions.DataSourceException;
import AudioDataSource.Exceptions.DataSourceExceptionCause;
import AudioDataSource.IAudioDataSource;
import SignalProcessing.Filters.FIR;
import Utils.Interval;

/**
 * Created by Alex on 08.02.2018.
 */
public class Equalizer implements IEffect
{
    FIR filter = null;
    private int max_chunk_size = 1024;

    @Override
    public String getName()
    {
        return "Equalizer";
    }

    @Override
    public void apply( IAudioDataSource dataSource, IAudioDataSource dataDest, Interval interval ) throws DataSourceException
    {
        if( filter.getFf_coeff_nr() % 2 == 0 )
        {
            throw new DataSourceException( "Equalization filter length must be an odd number", DataSourceExceptionCause.INVALID_PARAMETER );
        }
        if( interval.l < 0 )
        {
            interval.l = 0;
        }
        if( interval.r > dataSource.get_sample_number() )
        {
            interval.r = dataSource.get_sample_number();
        }
        Interval apply_interval = new Interval( interval.l + ( filter.getFf_coeff_nr() - 1 ) / 2, interval.get_length() );
        int zero_pad_right = Math.max( 0, apply_interval.r - dataSource.get_sample_number() );
        if( zero_pad_right > 0 )
        {
            double[] zeros = new double[ zero_pad_right ];
            double[][] samples = new double[ dataSource.get_channel_number() ][];
            int k;
            for( k = 0; k < samples.length; k++ )
            {
                samples[ k ] = zeros;
            }
            dataSource.put_samples( new AudioSamplesWindow( samples, dataSource.get_sample_number(), zero_pad_right, samples.length ) );
        }
        AudioSamplesWindow last_samples = dataSource.get_samples( apply_interval.r - filter.getFf_coeff_nr() / 2, filter.getFf_coeff_nr() / 2 );

        FIR_Filter effect1 = new FIR_Filter();
        effect1.setFilter( filter );
        effect1.setMax_chunk_size( max_chunk_size );
        effect1.apply( dataSource, dataDest, apply_interval );

        Left_Shift effect2 = new Left_Shift();
        effect2.setMax_chunk_size( max_chunk_size );
        effect2.setAmount( filter.getFf_coeff_nr() / 2 );
        effect2.apply( dataDest, dataDest, apply_interval );

        dataDest.put_samples( last_samples );
    }

    public void setFilter( FIR filter )
    {
        this.filter = filter;
    }

    public void setMax_chunk_size( int max_chunk_size )
    {
        this.max_chunk_size = max_chunk_size;
    }
}
