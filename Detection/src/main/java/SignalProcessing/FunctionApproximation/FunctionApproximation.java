package SignalProcessing.FunctionApproximation;

import Exceptions.DataSourceException;

/**
 * Created by Alex on 06.06.2018.
 */
public interface FunctionApproximation
{
    void prepare( double[] xs, double[] ys, int n ) throws DataSourceException;

    void get_values( double[] int_xs, double[] int_ys, int n ) throws DataSourceException;
}
