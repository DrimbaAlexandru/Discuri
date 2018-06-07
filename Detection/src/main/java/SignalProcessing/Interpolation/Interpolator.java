package SignalProcessing.Interpolation;

import Exceptions.DataSourceException;

/**
 * Created by Alex on 06.02.2018.
 */
public interface Interpolator
{
    void resize( double[] in, int n, double[] out, int m ) throws DataSourceException;
}
