package SignalProcessing.FunctionApproximation;

import Exceptions.DataSourceException;
import Exceptions.DataSourceExceptionCause;

/**
 * Created by Alex on 06.06.2018.
 */
public class LinearInterpolation implements FunctionApproximation
{
    private float[] xs;
    private float[] ys;
    private int n;

    @Override
    public void prepare( float[] xs, float[] ys, int n ) throws DataSourceException
    {
        int i;
        if( n > xs.length || n > ys.length )
        {
            throw new DataSourceException( "n larger than array size", DataSourceExceptionCause.INTERPOLATION_EXCEPTION );
        }
        this.xs = new float[ n ];
        this.ys = new float[ n ];
        for( i = 0; i < n; i++ )
        {
            this.xs[ i ] = xs[ i ];
            this.ys[ i ] = ys[ i ];
        }
        this.n = n;
    }

    @Override
    public void get_values( float[] int_xs, float[] int_ys, int int_n )throws DataSourceException
    {
        if( int_n> int_xs.length || int_n > int_ys.length )
        {
            throw new DataSourceException( "n larger than array size", DataSourceExceptionCause.INTERPOLATION_EXCEPTION );
        }
        int i;
        int j = 0;
        float xl, xr, yl, yr, m;
        for( i = 0; i < int_n; i++ )
        {
            while( ( j < n ) && ( int_xs[ i ] > xs[ j ] ) )
            {
                j++;
            }
            xr = ( j == n ) ? xs[ n - 1 ] + 1 : xs[ j ];
            yr = ( j == n ) ? ys[ n - 1 ] : ys[ j ];
            xl = ( j == 0 ) ? xs[ 0 ] - 1 : xs[ j - 1 ];
            yl = ( j == 0 ) ? ys[ 0 ] : ys[ j - 1 ];
            //m=(y1-y2)/x1-x2)
            m = ( ( yr - yl ) / ( xr - xl ) );
            int_ys[ i ] = m * int_xs[ i ] + ( yl - m * xl );
        }
    }
}
