package MarkerFile;

/**
 * Created by Alex on 10.11.2017.
 */
class Marking
{
    public int first_marked_sample;
    public int last_marked_sample;

    public Marking( int fs, int ls )
    {
        first_marked_sample = fs;
        last_marked_sample = ls;
    }

    @Override
    public String toString()
    {
        return first_marked_sample + "->" + last_marked_sample;
    }
}
