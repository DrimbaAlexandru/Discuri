package Utils;

import Exceptions.DataSourceException;
import Exceptions.DataSourceExceptionCause;

/**
 * Created by Alex on 26.06.2018.
 */
public class Stylus
{
    private double[] shape = null;
    private int side_length = 0;

    public Stylus( double width_um, double length_um, double RPM, double distance_from_spindle_mm, int sample_rate )
    {
        double sample_length = ( 2 * distance_from_spindle_mm * Math.PI * RPM / 60 ) / sample_rate * 1000;
        side_length = ( int )( length_um / 2 / sample_length );
        shape = new double[ side_length * 2 + 1 ];
        int i = 1;
        shape[ side_length ] = -( width_um / length_um ) * Math.sqrt( ( length_um / 2 ) * ( length_um / 2 ) );
        for( double x = sample_length; x < length_um / 2; x += sample_length )
        {
            shape[ side_length + i ] = -( width_um / length_um ) * Math.sqrt( ( length_um / 2 ) * ( length_um / 2 ) - x * x ) - shape[ side_length ];
            shape[ side_length - i ] = shape[ side_length + i ];
            i++;
        }
        shape[ side_length ] = 0;
    }

    public double get_offset( double[] samples, int position, boolean stylus_rides_on_top ) throws DataSourceException
    {
        int start_index = Math.max( 0, position - side_length );
        int end_index = Math.min( samples.length - 1, position + side_length );
        double max_offset = 0;
        if( stylus_rides_on_top )
        {
            for( int i = start_index; i < end_index; i++ )
            {
                max_offset = Math.max( max_offset, samples[ i ] - shape[ side_length + i - position ] - samples[ position ] );
            }
        }
        else
        {
            for( int i = start_index; i < end_index; i++ )
            {
                max_offset = Math.min( max_offset, samples[ i ] + shape[ side_length + i - position ] - samples[ position ] );
            }
        }
        return max_offset;
    }

    public int getSide_length()
    {
        return side_length;
    }
}
