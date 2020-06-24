package Effects;

import AudioDataSource.AudioSamplesWindow;
import AudioDataSource.IAudioDataSource;
import Utils.DataTypes.EffectType;
import Utils.Exceptions.DataSourceException;
import Utils.DataTypes.Interval;

/**
 * Created by Alex on 19.06.2018.
 */
public class Copy_to_ADS implements IEffect
{
    private int buffer_size = 1024 * 1024;
    private float progress = 0;

    public void setBuffer_size( int buffer_size )
    {
        this.buffer_size = buffer_size;
    }

    @Override
    public void apply( IAudioDataSource dataSource, IAudioDataSource dataDest, Interval interval ) throws DataSourceException
    {
        int temp_len;
        int i;
        AudioSamplesWindow win;
        interval.limit( 0, dataSource.get_sample_number() );
        progress = 0;
        for( i = interval.l; i < interval.r; )
        {
            temp_len = Math.min( buffer_size, interval.r - i );
            win = dataSource.get_samples( i, temp_len );
            dataDest.put_samples( win );
            i += temp_len;
            progress = 1.0f * ( i - interval.l ) / interval.get_length();
        }
    }

    @Override
    public float getProgress()
    {
        return progress;
    }

    @Override
    public EffectType getEffectType()
    {
        return EffectType.NORMAL_AUDIO_EFFECT;
    }
}
