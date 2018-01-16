package SignalProcessing.Effects;

import AudioDataSource.Exceptions.DataSourceException;
import AudioDataSource.IAudioDataSource;
import Utils.Interval;

/**
 * Created by Alex on 16.01.2018.
 */
public class Repair_Marked implements IEffect
{
    @Override
    public String getName()
    {
        return "Repair marked";
    }

    @Override
    public void apply( IAudioDataSource dataSource, Interval interval ) throws DataSourceException
    {

    }
}
