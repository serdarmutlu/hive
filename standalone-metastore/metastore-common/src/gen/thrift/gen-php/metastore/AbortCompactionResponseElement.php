<?php
namespace metastore;

/**
 * Autogenerated by Thrift Compiler (0.16.0)
 *
 * DO NOT EDIT UNLESS YOU ARE SURE THAT YOU KNOW WHAT YOU ARE DOING
 *  @generated
 */
use Thrift\Base\TBase;
use Thrift\Type\TType;
use Thrift\Type\TMessageType;
use Thrift\Exception\TException;
use Thrift\Exception\TProtocolException;
use Thrift\Protocol\TProtocol;
use Thrift\Protocol\TBinaryProtocolAccelerated;
use Thrift\Exception\TApplicationException;

class AbortCompactionResponseElement
{
    static public $isValidate = false;

    static public $_TSPEC = array(
        1 => array(
            'var' => 'compactionId',
            'isRequired' => true,
            'type' => TType::I64,
        ),
        2 => array(
            'var' => 'status',
            'isRequired' => false,
            'type' => TType::STRING,
        ),
        3 => array(
            'var' => 'message',
            'isRequired' => false,
            'type' => TType::STRING,
        ),
    );

    /**
     * @var int
     */
    public $compactionId = null;
    /**
     * @var string
     */
    public $status = null;
    /**
     * @var string
     */
    public $message = null;

    public function __construct($vals = null)
    {
        if (is_array($vals)) {
            if (isset($vals['compactionId'])) {
                $this->compactionId = $vals['compactionId'];
            }
            if (isset($vals['status'])) {
                $this->status = $vals['status'];
            }
            if (isset($vals['message'])) {
                $this->message = $vals['message'];
            }
        }
    }

    public function getName()
    {
        return 'AbortCompactionResponseElement';
    }


    public function read($input)
    {
        $xfer = 0;
        $fname = null;
        $ftype = 0;
        $fid = 0;
        $xfer += $input->readStructBegin($fname);
        while (true) {
            $xfer += $input->readFieldBegin($fname, $ftype, $fid);
            if ($ftype == TType::STOP) {
                break;
            }
            switch ($fid) {
                case 1:
                    if ($ftype == TType::I64) {
                        $xfer += $input->readI64($this->compactionId);
                    } else {
                        $xfer += $input->skip($ftype);
                    }
                    break;
                case 2:
                    if ($ftype == TType::STRING) {
                        $xfer += $input->readString($this->status);
                    } else {
                        $xfer += $input->skip($ftype);
                    }
                    break;
                case 3:
                    if ($ftype == TType::STRING) {
                        $xfer += $input->readString($this->message);
                    } else {
                        $xfer += $input->skip($ftype);
                    }
                    break;
                default:
                    $xfer += $input->skip($ftype);
                    break;
            }
            $xfer += $input->readFieldEnd();
        }
        $xfer += $input->readStructEnd();
        return $xfer;
    }

    public function write($output)
    {
        $xfer = 0;
        $xfer += $output->writeStructBegin('AbortCompactionResponseElement');
        if ($this->compactionId !== null) {
            $xfer += $output->writeFieldBegin('compactionId', TType::I64, 1);
            $xfer += $output->writeI64($this->compactionId);
            $xfer += $output->writeFieldEnd();
        }
        if ($this->status !== null) {
            $xfer += $output->writeFieldBegin('status', TType::STRING, 2);
            $xfer += $output->writeString($this->status);
            $xfer += $output->writeFieldEnd();
        }
        if ($this->message !== null) {
            $xfer += $output->writeFieldBegin('message', TType::STRING, 3);
            $xfer += $output->writeString($this->message);
            $xfer += $output->writeFieldEnd();
        }
        $xfer += $output->writeFieldStop();
        $xfer += $output->writeStructEnd();
        return $xfer;
    }
}
