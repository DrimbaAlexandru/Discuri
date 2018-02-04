package SignalProcessing.Effects;

import AudioDataSource.Exceptions.DataSourceException;
import AudioDataSource.IAudioDataSource;
import Utils.Interval;

/**
 * Created by Alex on 11.01.2018.
 */
public interface IEffect
{
    String getName();
    void apply( IAudioDataSource dataSource, IAudioDataSource dataDest, Interval interval ) throws DataSourceException;
}
