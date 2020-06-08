package SignalProcessing.LinearPrediction;

import Utils.Exceptions.DataSourceException;
import Utils.Exceptions.DataSourceExceptionCause;

/**
 * Created by Alex on 12.06.2018.
 */
public class LinearPrediction
{
    private double a[];
    private int coeffs;

    public LinearPrediction( double[] a, int nr_coeffs )
    {
        this.a = a;
        coeffs = nr_coeffs;
    }

    public void predict_forward( double[] buffer, int start_offset, int end_offset ) throws DataSourceException
    {
        if( start_offset > end_offset || start_offset < coeffs + 1 )
        {
            throw new DataSourceException( "Start offset must be smaller than end offset and greater than the order", DataSourceExceptionCause.INVALID_PARAMETER );
        }
        int n;
        for( n = start_offset; n < end_offset; n++ )
        {
            buffer[ n ] = 0;
            for( int i = 1; i <= coeffs; i++ )
            {
                buffer[ n ] -= a[ i ] * buffer[ n - i ];
            }
        }
    }

    public void predict_backward( double[] buffer, int start_offset, int end_offset ) throws DataSourceException
    {
        if( start_offset < end_offset || start_offset + coeffs + 1 > buffer.length )
        {
            throw new DataSourceException( "Start offset must be larger than end offset and smaller than ( buffer.length - order )", DataSourceExceptionCause.INVALID_PARAMETER );
        }
        int n;
        for( n = start_offset; n > end_offset; n-- )
        {
            buffer[ n ] = 0;
            for( int i = 1; i <= coeffs; i++ )
            {
                buffer[ n ] -= a[ i ] * buffer[ n + i ];
            }
        }
    }
}
