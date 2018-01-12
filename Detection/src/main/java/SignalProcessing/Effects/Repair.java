package SignalProcessing.Effects;

import AudioDataSource.Exceptions.DataSourceException;
import AudioDataSource.IAudioDataSource;
import Utils.Interval;

/**
 * Created by Alex on 11.01.2018.
 */
public class Repair implements IEffect
{

    @Override
    public String getName()
    {
        return "Repair";
    }

    @Override
    public void apply( IAudioDataSource dataSource, Interval interval ) throws DataSourceException
    {

    }
}
