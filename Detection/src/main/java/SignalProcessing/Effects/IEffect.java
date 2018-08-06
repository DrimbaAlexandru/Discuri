package SignalProcessing.Effects;

import Exceptions.DataSourceException;
import AudioDataSource.IAudioDataSource;
import Utils.Interval;

/**
 * Created by Alex on 11.01.2018.
 */
public interface IEffect
{
    void apply( IAudioDataSource dataSource, IAudioDataSource dataDest, Interval interval ) throws DataSourceException;
    float getProgress();
}
