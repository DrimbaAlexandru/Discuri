package AudioDataSource.FileADS;

import Exceptions.DataSourceException;
import Exceptions.DataSourceExceptionCause;
import AudioDataSource.AudioSamplesWindow;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Created by Alex on 12.12.2017.
 */

public class AUFileAudioSource implements IFileAudioDataSource
{
    private int channel_number;
    private int sample_number;
    private int sample_rate;
    private int byte_depth;
    private int frame_size;
    private static ByteOrder bo = ByteOrder.BIG_ENDIAN;

    private static final int file_magic_number =  0x2e736e64;

    private static final int _8_bit_linear_PCM = 2;
    private static final int _16_bit_linear_PCM = 3;
    private static final int _32_bit_linear_PCM = 5;

    private int header_length;
    private int data_length;
    private RandomAccessFile file;
    private String file_path;

    private static final int buffer_size = 1024 * 8; //must be a multiple of 8
    private static final byte buffer[] = new byte[ buffer_size ];
    private ByteBuffer byteBuffer = ByteBuffer.wrap( buffer );

    public AUFileAudioSource( String path ) throws DataSourceException
    {
        try
        {
            file_path = path;
            file = new RandomAccessFile( path, "rw" );
            readHeader();
        }
        catch( IOException e )
        {
            throw new DataSourceException( e.getMessage(), DataSourceExceptionCause.IO_ERROR );
        }
        frame_size = channel_number * byte_depth;
    }

    public AUFileAudioSource( String path, int channel_number, int sample_rate, int byte_depth ) throws DataSourceException
    {
        this.channel_number = channel_number;
        this.sample_number = 0;
        this.sample_rate = sample_rate;
        this.byte_depth = byte_depth;
        header_length = 24;
        data_length = 0;
        frame_size = channel_number * byte_depth;
        file_path = path;
        try
        {
            file = new RandomAccessFile( path, "rw" );
            byteBuffer.order( bo );
            writeHeader();
        }
        catch( IOException e )
        {
            throw new DataSourceException( e.getMessage(), DataSourceExceptionCause.IO_ERROR );
        }
    }

    private void readHeader() throws IOException, DataSourceException
    {
        int temp;
        file.seek( 0 );
        if( 24 != file.read( buffer, 0, 24 ) )
        {
            throw new DataSourceException( "Could not read file header. Read returned less than 24 bits.", DataSourceExceptionCause.BAD_FILE_FORMAT );
        }
        //Word 0 : Magic number and deduce endianness
        byteBuffer.order( ByteOrder.BIG_ENDIAN );
        temp = byteBuffer.getInt( 0 );

        if( temp == file_magic_number )
        {
            bo = ByteOrder.BIG_ENDIAN;
        }
        else
        {
            byteBuffer.order( ByteOrder.LITTLE_ENDIAN );
            temp = byteBuffer.getInt( 4 );
            if( temp == file_magic_number )
            {
                bo = ByteOrder.LITTLE_ENDIAN;
            }
            else
            {
                throw new DataSourceException( "Invalid AU file format", DataSourceExceptionCause.BAD_FILE_FORMAT );
            }
        }

        //Word 1 : Data offset
        header_length = byteBuffer.getInt( 4 );
        //Word 2 : Data size
        data_length = byteBuffer.getInt( 8 );
        //Word 3 : Format
        temp = byteBuffer.getInt( 12 );
        switch( temp )
        {
            case _8_bit_linear_PCM: byte_depth = 1; break;
            case _16_bit_linear_PCM: byte_depth = 2; break;
            case _32_bit_linear_PCM: byte_depth = 4; break;
            default: throw  new DataSourceException( "Unsupported AU file format", DataSourceExceptionCause.UNSUPPORTED_FILE_FORMAT );
        }
        //Word 4 : Sample rate
        sample_rate = byteBuffer.getInt( 16 );
        //Word 5 : Channel number
        channel_number = byteBuffer.getInt( 20 );
        sample_number = data_length / byte_depth / channel_number;
    }

    private void writeHeader() throws DataSourceException
    {
        data_length = sample_number * frame_size;
        byteBuffer.putInt( 0, file_magic_number );
        byteBuffer.putInt( 4, header_length );
        byteBuffer.putInt( 8, data_length );
        switch( byte_depth )
        {
            case 1: byteBuffer.putInt( 12, _8_bit_linear_PCM ); break;
            case 2: byteBuffer.putInt( 12, _16_bit_linear_PCM ); break;
            case 4: byteBuffer.putInt( 12, _32_bit_linear_PCM ); break;
            default: throw new DataSourceException( "Unsupported AU file format", DataSourceExceptionCause.UNSUPPORTED_FILE_FORMAT );
        }
        byteBuffer.putInt( 16, sample_rate );
        byteBuffer.putInt( 20, channel_number );
        try
        {
            file.seek( 0 );
            file.write( buffer, 0, header_length );
        }
        catch( IOException e )
        {
            throw new DataSourceException( e.getMessage(), DataSourceExceptionCause.IO_ERROR );
        }
    }

    @Override
    public int get_channel_number()
    {
        return channel_number;
    }

    @Override
    public int get_sample_number()
    {
        return sample_number;
    }

    @Override
    public int get_sample_rate()
    {
        return sample_rate;
    }

    public int getByte_depth()
    {
        return byte_depth;
    }

    @Override
    public AudioSamplesWindow get_samples( int first_sample_index, int length ) throws DataSourceException
    {
        first_sample_index = Math.min( first_sample_index, get_sample_number() );
        first_sample_index = Math.max( 0, first_sample_index );
        length = Math.min( length, sample_number - first_sample_index );

        float data[][] = new float[ channel_number ][ length ];
        int i, k;
        int buffer_position = 0, read_bytes = 0;
        try
        {
            file.seek( first_sample_index * frame_size + header_length );
        }
        catch( IOException e )
        {
            throw new DataSourceException( e.getMessage(), DataSourceExceptionCause.IO_ERROR );
        }
        for( i = 0; i < length; i++ )
        {
            if( buffer_position >= buffer_size || buffer_position == 0 )
            {
                try
                {
                    read_bytes = file.read( buffer, 0, buffer_size );
                    buffer_position = 0;
                }
                catch( IOException e )
                {
                    throw new DataSourceException( e.getMessage(), DataSourceExceptionCause.IO_ERROR );
                }
            }
            if( buffer_position >= read_bytes )
            {
                throw new DataSourceException( "Tried to read outside of buffered values", DataSourceExceptionCause.IO_ERROR );
            }
            else
            {
                for( k = 0; k < channel_number; k++ )
                {
                    data[ k ][ i ] = get_sample( buffer_position );
                    buffer_position += byte_depth;
                }
            }
        }
        return new AudioSamplesWindow( data, first_sample_index, length, channel_number );
    }

    private float get_sample( int buffer_offset )
    {
        switch( byte_depth )
        {
            case 1:
                return ( ( float )byteBuffer.get( buffer_offset ) ) / Byte.MAX_VALUE;
            case 2:
                return ( ( float )byteBuffer.getShort( buffer_offset ) ) / ( Short.MAX_VALUE + 1 );
            case 4:
                return ( ( float )byteBuffer.getInt( buffer_offset ) ) / ( ( long )( Integer.MAX_VALUE ) + 1 );
        }
        return 0;
    }

    private void put_sample( int buffer_offset, float sample )
    {
        switch( byte_depth )
        {
            case 1:
                if( sample <= -1 )
                {
                    byteBuffer.put( buffer_offset, Byte.MIN_VALUE );
                }
                else if( sample >= 1 )
                {
                    byteBuffer.put( buffer_offset, Byte.MAX_VALUE );
                }
                else
                {
                    byteBuffer.put( buffer_offset, ( byte )( sample * Byte.MAX_VALUE ) );
                }
                break;
            case 2:
                if( sample <= -1 )
                {
                    byteBuffer.putShort( buffer_offset, Short.MIN_VALUE );
                }
                else if( sample >= 1 )
                {
                    byteBuffer.putShort( buffer_offset, Short.MAX_VALUE );
                }
                else
                {
                    byteBuffer.putShort( buffer_offset, ( short )( sample * ( Short.MAX_VALUE + 1 ) ) );
                }
                break;
            case 4:
                if( sample <= -1 )
                {
                    byteBuffer.putInt( buffer_offset, Integer.MIN_VALUE );
                }
                else if( sample >= 1 )
                {
                    byteBuffer.putInt( buffer_offset, Integer.MAX_VALUE );
                }
                else
                {
                    byteBuffer.putInt( buffer_offset, ( int )( sample * ( ( long )Integer.MAX_VALUE + 1 ) ) );
                }
                break;
        }
    }

    @Override
    public void put_samples( AudioSamplesWindow win ) throws DataSourceException
    {
        int i, k;
        int buffer_position = 0;
        try
        {
            file.seek( win.get_first_sample_index() * frame_size + header_length );
        }
        catch( IOException e )
        {
            throw new DataSourceException( e.getMessage(), DataSourceExceptionCause.IO_ERROR );
        }
        for( i = win.get_first_sample_index(); i < win.get_first_sample_index() + win.get_length(); i++ )
        {
            for( k = 0; k < channel_number; k++ )
            {
                if( buffer_position > buffer_size - byte_depth )
                {
                    throw new DataSourceException( "Tried to write outside of buffered values", DataSourceExceptionCause.IO_ERROR );
                }

                put_sample( buffer_position, win.getSample( i, k ) );
                buffer_position += byte_depth;
            }

            if( buffer_position > ( buffer_size - byte_depth * channel_number ) || ( i == win.get_first_sample_index() + win.get_length() - 1 ) )
            {
                try
                {
                    file.write( buffer, 0, buffer_position );
                    buffer_position = 0;
                }
                catch( IOException e )
                {
                    throw new DataSourceException( e.getMessage(), DataSourceExceptionCause.IO_ERROR );
                }
            }
        }
        sample_number = Math.max( sample_number, win.get_first_sample_index() + win.get_length() );
        writeHeader();
    }

    @Override
    public void close() throws DataSourceException
    {
        try
        {
            file.close();
        }
        catch( IOException e )
        {
            throw new DataSourceException( e.getMessage(), DataSourceExceptionCause.IO_ERROR );
        }
    }

    public String getFile_path()
    {
        return file_path;
    }
}
