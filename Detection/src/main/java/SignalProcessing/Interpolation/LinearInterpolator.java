package SignalProcessing.Interpolation;


import Exceptions.DataSourceException;
import Exceptions.DataSourceExceptionCause;

/**
 * Created by Alex on 06.02.2018.
 */
public class LinearInterpolator implements Interpolator
{
    @Override
    public void resize( double[] in, int n, double[] out, int m ) throws DataSourceException
    {
        if( in.length > n || out.length > m )
        {
            throw new DataSourceException( "Invalid array sizes", DataSourceExceptionCause.INVALID_PARAMETER );
        }

        int i;
        double rel_in_index;
        int left_in_index;
        for( i = 0; i < m - 1; i++ )
        {
            rel_in_index = ( double )i / ( m - 1 ) * ( n - 1 );
            left_in_index = ( int )rel_in_index;
            out[ i ] = ( rel_in_index - left_in_index ) * in[ left_in_index + 1 ] + ( 1 - ( rel_in_index - left_in_index ) ) * in[ left_in_index ];
        }
        out[ m - 1 ] = in[ n - 1 ];
    }
}
