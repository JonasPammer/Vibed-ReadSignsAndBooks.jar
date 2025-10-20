package Anvil;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.Arrays;

public class NBTTagLongArray extends NBTBase
{
    private long[] data;

    NBTTagLongArray() {}

    public NBTTagLongArray(long[] p_i45132_1_)
    {
        this.data = p_i45132_1_;
    }

    void write(DataOutput p_74734_1_) throws IOException
    {
        p_74734_1_.writeInt(this.data.length);

        for (int i = 0; i < this.data.length; ++i)
        {
            p_74734_1_.writeLong(this.data[i]);
        }
    }

    void func_152446_a(DataInput p_152446_1_, int p_152446_2_, NBTSizeTracker p_152446_3_) throws IOException
    {
        p_152446_3_.func_152450_a(192L);
        int i = p_152446_1_.readInt();
        p_152446_3_.func_152450_a((long)(64 * i));
        this.data = new long[i];

        for (int j = 0; j < i; ++j)
        {
            this.data[j] = p_152446_1_.readLong();
        }
    }

    public byte getId()
    {
        return (byte)12;
    }

    public String toString()
    {
        String s = "[";
        long[] along = this.data;
        int i = along.length;

        for (int j = 0; j < i; ++j)
        {
            long k = along[j];
            s = s + k + ",";
        }

        return s + "]";
    }

    public NBTBase copy()
    {
        long[] along = new long[this.data.length];
        System.arraycopy(this.data, 0, along, 0, this.data.length);
        return new NBTTagLongArray(along);
    }

    public boolean equals(Object p_equals_1_)
    {
        return super.equals(p_equals_1_) ? Arrays.equals(this.data, ((NBTTagLongArray)p_equals_1_).data) : false;
    }

    public int hashCode()
    {
        return super.hashCode() ^ Arrays.hashCode(this.data);
    }

    public long[] func_150306_c()
    {
        return this.data;
    }
}

