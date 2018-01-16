package AudioDataSource.FileADS;

import javafx.util.Pair;

import java.io.FileNotFoundException;
import java.io.RandomAccessFile;
import java.util.LinkedList;

/**
 * Created by Alex on 15.12.2017.
 */
public class FileStreamManager
{
    private static int capacity = 32;
    private static LinkedList< Pair< String, RandomAccessFile > > pool = new LinkedList<>();


    public static void setCapacity( int new_capacity )
    {
        capacity = new_capacity;
    }

    public static RandomAccessFile get_accessor( String file_name )
    {
        Pair< String, RandomAccessFile > found = null;

        for( Pair< String, RandomAccessFile > p : pool )
        {
            if( p.getKey().equals( file_name ) )
            {
                found = p;
                break;
            }
        }

        if( found != null )
        {
            pool.remove( found );
            pool.addFirst( found );
            return found.getValue();
        }
        else
        {
            while( pool.size() >= capacity )
            {
                pool.removeLast();
            }
            try
            {
                pool.addFirst( new Pair<>( file_name, new RandomAccessFile( file_name, "rw" ) ) );
                return pool.getFirst().getValue();
            }
            catch( FileNotFoundException e )
            {
                return null;
            }
        }
    }
}
