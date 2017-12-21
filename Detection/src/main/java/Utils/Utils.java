package Utils;

/**
 * Created by Alex on 21.12.2017.
 */
public class Utils
{
    public static < T > boolean memeq( T array[], int offset, int length, T value )
    {
        int i;
        for(i=offset;i<offset+length;i++)
        {
            if( !array[ i ].equals( value ) )
            {
                return false;
            }
        }
        return true;
    }
}
