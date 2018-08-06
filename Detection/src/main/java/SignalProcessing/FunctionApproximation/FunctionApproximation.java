package SignalProcessing.FunctionApproximation;

import Exceptions.DataSourceException;

/**
 * Created by Alex on 06.06.2018.
 */
public interface FunctionApproximation
{
    void prepare( float[] xs, float[] ys, int n ) throws DataSourceException;

    void get_values( float[] int_xs, float[] int_ys, int n ) throws DataSourceException;
}
