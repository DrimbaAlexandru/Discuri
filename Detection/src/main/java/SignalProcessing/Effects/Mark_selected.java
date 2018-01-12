package SignalProcessing.Effects;

import AudioDataSource.Exceptions.DataSourceException;
import AudioDataSource.Exceptions.DataSourceExceptionCause;
import AudioDataSource.IAudioDataSource;
import MarkerFile.MarkerFile;
import ProjectStatics.ProjectStatics;
import Utils.Interval;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by Alex on 11.01.2018.
 */
public class Mark_selected implements IEffect
{
    private List< Integer > affected_channels = new ArrayList<>();

    @Override

    public String getName()
    {
        return "Mark selected";
    }

    @Override
    public void apply( IAudioDataSource dataSource, Interval interval ) throws DataSourceException
    {
        MarkerFile mf = ProjectStatics.getMarkerFile();
        if( mf == null )
        {
            throw new DataSourceException( "No marker file was set", DataSourceExceptionCause.IO_ERROR );
        }
        else
        {
            for( int ch : affected_channels )
            {
                mf.addMark( interval.l, interval.r - 1, ch );
            }
            if( affected_channels.size() == 0 )
            {
                throw new DataSourceException( "No channels were selected. The opperation had no effect.", DataSourceExceptionCause.WARNING );
            }
        }
    }

    public void setAffected_channels( List<Integer> chs )
    {
        affected_channels.clear();
        affected_channels.addAll( chs );
    }
}
