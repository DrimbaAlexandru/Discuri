package AudioDataSource.FileADS;

import AudioDataSource.Exceptions.DataSourceException;
import AudioDataSource.Exceptions.DataSourceExceptionCause;
import AudioDataSource.ADCache.AudioSamplesWindow;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Created by Alex on 12.12.2017.
 */

public class WAVFileAudioSource implements IFileAudioDataSource
{
    private int channel_number;
    private int sample_number;
    private int sample_rate;
    private int byte_depth;
    private int frame_size;
    private static ByteOrder bo = ByteOrder.LITTLE_ENDIAN;

    private static final int chunkID = 0x46464952;
    private static final int fmt_chunk_ID = 0x20746d66;
    private static final int data_chunk_ID = 0x61746164;
    private static final int format = 0x45564157;

    private int chunk_size;
    private int fmt_chunk_size;
    private int data_chunk_size;
    private int fmt_chunk_offset;
    private int data_chunk_offset;
    private RandomAccessFile file;
    private String file_path;

    private static final int buffer_size = 1024 * 8; //must be a multiple of 8
    private static final byte buffer[] = new byte[ buffer_size ];
    private ByteBuffer byteBuffer = ByteBuffer.wrap( buffer );

    public WAVFileAudioSource( String path ) throws DataSourceException
    {
        try
        {
            file_path = path;
            file = new RandomAccessFile( path, "rw" );
            byteBuffer.order( bo );
            readHeader();
        }
        catch( IOException e )
        {
            throw new DataSourceException( e.getMessage(), DataSourceExceptionCause.IO_ERROR );
        }
        frame_size = channel_number * byte_depth;
    }

    public WAVFileAudioSource( String path, int channel_number, int sample_rate, int byte_depth ) throws DataSourceException
    {
        file_path = path;
        this.channel_number = channel_number;
        this.sample_number = 0;
        this.sample_rate = sample_rate;
        this.byte_depth = byte_depth;
        fmt_chunk_size = 16;
        data_chunk_size = 0;
        fmt_chunk_offset = 12;
        data_chunk_offset = fmt_chunk_offset + fmt_chunk_size + 8;
        chunk_size = 8 + fmt_chunk_size + 8 + data_chunk_size + 8;
        frame_size = channel_number * byte_depth;
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
        short s_temp;
        int bytes_read = 0;
        boolean is_fmt_present = false, is_data_present = false;

        file.seek( 0 );
        if( 12 != file.read( buffer, 0, 12 ) )
        {
            throw new DataSourceException( "Could not read file header. Read returned less than required.", DataSourceExceptionCause.BAD_FILE_FORMAT );
        }
        //Chunk ID
        temp = byteBuffer.getInt( 0 );
        if( temp != chunkID )
        {
            throw new DataSourceException( "Invalid WAV header file", DataSourceExceptionCause.BAD_FILE_FORMAT );
        }

        //Chunk size
        chunk_size = byteBuffer.getInt( 4 );

        //Format
        temp = byteBuffer.getInt( 8 );
        if( temp != format )
        {
            throw new DataSourceException( "Invalid WAV header file", DataSourceExceptionCause.BAD_FILE_FORMAT );
        }
        bytes_read += 12;

        while( bytes_read < chunk_size + 8 )
        {
            file.seek( bytes_read );
            if( 8 != file.read( buffer, 0, 8 ) )
            {
                throw new DataSourceException( "Could not read file header. Read returned less than required.", DataSourceExceptionCause.BAD_FILE_FORMAT );
            }
            int subchunkID = byteBuffer.getInt( 0 );
            int subchunkSize = byteBuffer.getInt( 4 );
            switch( subchunkID )
            {
                case fmt_chunk_ID:
                {
                    is_fmt_present = true;
                    fmt_chunk_offset = bytes_read;
                    fmt_chunk_size = subchunkSize;
                    if( fmt_chunk_size != file.read( buffer, 0, fmt_chunk_size ) )
                    {
                        throw new DataSourceException( "Could not read file header. Read returned less than required.", DataSourceExceptionCause.BAD_FILE_FORMAT );
                    }
                    if( fmt_chunk_size < 16 )
                    {
                        throw new DataSourceException( "Invalid file header", DataSourceExceptionCause.BAD_FILE_FORMAT );
                    }
                    //Audio format
                    s_temp = byteBuffer.getShort( 0 );
                    if( s_temp != 1 )
                    {
                        throw new DataSourceException( "Unsupported file format", DataSourceExceptionCause.UNSUPPORTED_FILE_FORMAT );
                    }

                    //Num channels
                    s_temp = byteBuffer.getShort( 2 );
                    channel_number = s_temp;

                    //Sample rate
                    sample_rate = byteBuffer.getInt( 4 );

                    //Byte rate
                    //We ignore this.

                    //Block align
                    //We ignore this

                    s_temp = byteBuffer.getShort( 14 );
                    if( s_temp % 8 != 0 )
                    {
                        throw new DataSourceException( "Unsupported file format", DataSourceExceptionCause.UNSUPPORTED_FILE_FORMAT );
                    }
                    byte_depth = s_temp / 8;
                    bytes_read += subchunkSize + 8;
                    break;
                }
                case data_chunk_ID:
                {
                    is_data_present = true;
                    data_chunk_offset = bytes_read;
                    data_chunk_size = subchunkSize;
                    bytes_read += subchunkSize + 8;
                    break;
                }
                default:
                {
                    System.err.println( "Unsupported WAV subchunk ID " + subchunkID + " at offset " + bytes_read );
                    bytes_read += subchunkSize + 8;
                }
            }
        }

        if( !is_data_present || !is_fmt_present )
        {
            throw new DataSourceException( "Invalid file. FMT or DATA subchunks not found", DataSourceExceptionCause.BAD_FILE_FORMAT );
        }

        sample_number = data_chunk_size / byte_depth / channel_number;
    }

    private void writeHeader() throws DataSourceException
    {
        data_chunk_size = sample_number * byte_depth * channel_number;
        chunk_size = 8 + fmt_chunk_size + 8 + data_chunk_size;
        try
        {
            byteBuffer.putInt( 0, chunkID );
            byteBuffer.putInt( 4, chunk_size );
            byteBuffer.putInt( 8, format );
            file.seek( 0 );
            file.write( buffer, 0, 12 );

            byteBuffer.putInt( 0, fmt_chunk_ID );
            byteBuffer.putInt( 4, fmt_chunk_size );
            byteBuffer.putShort( 8, ( short )1 );
            byteBuffer.putShort( 10, ( short )channel_number );
            byteBuffer.putInt( 12, sample_rate );
            byteBuffer.putInt( 16, sample_rate * channel_number * byte_depth );
            byteBuffer.putShort( 20, ( short )( channel_number * byte_depth ) );
            byteBuffer.putShort( 22, ( short )( 8 * byte_depth ) );

            file.seek( fmt_chunk_offset );
            file.write( buffer, 0, 24 );

            byteBuffer.putInt( 0, data_chunk_ID );
            byteBuffer.putInt( 4, data_chunk_size );

            file.seek( data_chunk_offset );
            file.write( buffer, 0, 8 );
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
        double data[][] = new double[ channel_number ][ length ];
        int i, k;
        int buffer_position = 0, read_bytes = 0;
        try
        {
            file.seek( first_sample_index * frame_size + data_chunk_offset + 8 );
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

    private double get_sample( int buffer_offset )
    {
        switch( byte_depth )
        {
            case 1:
                double val = ( double )byteBuffer.get( buffer_offset );
                if( val < 0 )
                {
                    val += 256;
                }
                return val / 128 - 1;
            case 2:
                return ( ( double )byteBuffer.getShort( buffer_offset ) ) / ( Short.MAX_VALUE + 1 );
            case 4:
                return ( ( double )byteBuffer.getInt( buffer_offset ) ) / ( ( long )( Integer.MAX_VALUE ) + 1 );
        }
        return 0;
    }

    private void put_sample( int buffer_offset, double sample )
    {
        switch( byte_depth )
        {
            case 1:
                if( sample <= -1 )
                {
                    byteBuffer.put( buffer_offset, ( byte )0 );
                }
                else if( sample >= 1 )
                {
                    byteBuffer.put( buffer_offset, ( byte )-1 );
                }
                else
                {
                    if( sample < 0 )
                    {
                        byteBuffer.put( buffer_offset, ( byte )( ( sample + 1 ) * 128 ) );
                    }
                    else
                    {
                        byteBuffer.put( buffer_offset, ( byte )( ( sample - 1 ) * 128 ) );
                    }
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
            file.seek( win.get_first_sample_index() * frame_size + data_chunk_offset + 8 );
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
