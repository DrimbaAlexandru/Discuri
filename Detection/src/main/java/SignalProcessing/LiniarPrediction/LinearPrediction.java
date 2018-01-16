package SignalProcessing.LiniarPrediction;

import AudioDataSource.Exceptions.DataSourceException;

/**
 * Created by Alex on 10.11.2017.
 */
public interface LinearPrediction
{
    void extrapolate( double[] left, double[] center, double[] right, int l_length, int c_length, int r_length ) throws DataSourceException;
}
