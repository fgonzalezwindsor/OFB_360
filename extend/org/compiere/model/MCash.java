/******************************************************************************
 * Product: Adempiere ERP & CRM Smart Business Solution                       *
 * Copyright (C) 1999-2006 ComPiere, Inc. All Rights Reserved.                *
 * This program is free software; you can redistribute it and/or modify it    *
 * under the terms version 2 of the GNU General Public License as published   *
 * by the Free Software Foundation. This program is distributed in the hope   *
 * that it will be useful, but WITHOUT ANY WARRANTY; without even the implied *
 * warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.           *
 * See the GNU General Public License for more details.                       *
 * You should have received a copy of the GNU General Public License along    *
 * with this program; if not, write to the Free Software Foundation, Inc.,    *
 * 59 Temple Place, Suite 330, Boston, MA 02111-1307 USA.                     *
 * For the text or an alternative of this public license, you may reach us    *
 * ComPiere, Inc., 2620 Augustine Dr. #245, Santa Clara, CA 95054, USA        *
 * or via info@compiere.org or http://www.compiere.org/license.html           *
 *****************************************************************************/
package org.compiere.model;

import java.io.File;
import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.util.Calendar;
import java.util.List;
import java.util.Properties;
import java.util.logging.Level;

import org.compiere.process.DocAction;
import org.compiere.process.DocumentEngine;
import org.compiere.util.CLogger;
import org.compiere.util.DB;
import org.compiere.util.DisplayType;
import org.compiere.util.Env;
import org.compiere.util.Msg;
import org.compiere.util.TimeUtil;
import org.ofb.model.OFBForward;

/**
 *	Cash Journal Model
 *	
 *  @author Jorg Janke
 *  @version $Id: MCash.java,v 1.3 2006/07/30 00:51:03 jjanke Exp $
 *  @author victor.perez@e-evolution.com, e-Evolution http://www.e-evolution.com
 *  <li>FR [ 1866214 ]  
 *  @see http://sourceforge.net/tracker/index.php?func=detail&aid=1866214&group_id=176962&atid=879335
 * 	<li> FR [ 2520591 ] Support multiples calendar for Org 
 *	@see http://sourceforge.net/tracker2/?func=detail&atid=879335&aid=2520591&group_id=176962 	
 *  @author Teo Sarca, SC ARHIPAC SERVICE SRL
 * 			<li>BF [ 1831997 ] Cash journal allocation reversed
 * 			<li>BF [ 1894524 ] Pay an reversed invoice
 * 			<li>BF [ 1899477 ] MCash.getLines should return only active lines
 * 			<li>BF [ 2588326 ] Cash Lines are not correctly updated on voiding
 */
public class MCash extends X_C_Cash implements DocAction
{
	/**
	 * 
	 */
	private static final long serialVersionUID = -1221144207418749593L;


	/**
	 * 	Get Cash Journal for currency, org and date
	 *	@param ctx context
	 *	@param C_Currency_ID currency
	 *	@param AD_Org_ID org
	 *	@param dateAcct date
	 *	@param trxName transaction
	 *	@return cash
	 */
	public static MCash get (Properties ctx, int AD_Org_ID, 
		Timestamp dateAcct, int C_Currency_ID, String trxName)
	{
		//	Existing Journal
		final String whereClause = "C_Cash.AD_Org_ID=?"						//	#1
		//+ " AND TRUNC(C_Cash.StatementDate)=?"			//	#2
		+ " And Trunc(C_Cash.StatementDate)=to_date('"+TimeUtil.getDay(dateAcct).toString().substring(0, 10)+"','yyyy-mm-dd')" //faaguilar OFB patch fecha		
		+ " AND C_Cash.Processed='N'"
		+ " AND EXISTS (SELECT * FROM C_CashBook cb "
			+ "WHERE C_Cash.C_CashBook_ID=cb.C_CashBook_ID AND cb.AD_Org_ID=C_Cash.AD_Org_ID"
			+ " AND cb.C_Currency_ID=?)";			//	#3
		MCash retValue = new Query(ctx, I_C_Cash.Table_Name, whereClause, trxName)
		  .setParameters(AD_Org_ID,C_Currency_ID)
			//.setParameters(AD_Org_ID,TimeUtil.getDay(dateAcct),C_Currency_ID)
			.first()
		;
		
		if (retValue != null)
			return retValue;
		
		//	Get CashBook
		MCashBook cb = MCashBook.get (ctx, AD_Org_ID, C_Currency_ID);
		if (cb == null)
		{
			s_log.warning("No CashBook for AD_Org_ID=" + AD_Org_ID + ", C_Currency_ID=" + C_Currency_ID);
			return null;
		}
		
		//	Create New Journal
		retValue = new MCash (cb, dateAcct);
		retValue.save(trxName);
		return retValue;
	}	//	get

	/**
	 * 	Get Cash Journal for CashBook and date
	 *	@param ctx context
	 *	@param C_CashBook_ID cashbook
	 *	@param dateAcct date
	 *	@param trxName transaction
	 *	@return cash
	 */
	public static MCash get (Properties ctx, int C_CashBook_ID, 
		Timestamp dateAcct, String trxName)
	{
		final String whereClause ="C_CashBook_ID=?"			//	#1
//			+ " AND TRUNC(c.StatementDate)=?"			//	#2 comment by faaguilar
			+ " And Trunc(StatementDate)=to_date('"+TimeUtil.getDay(dateAcct).toString().substring(0, 10)+"','yyyy-mm-dd')" //faaguilar OFB patch fecha
				+ " AND Processed='N'";
		
		MCash retValue = new Query(ctx, MCash.Table_Name, whereClause, trxName)
		.setParameters(C_CashBook_ID)
			//.setParameters(C_CashBook_ID, TimeUtil.getDay(dateAcct))
			.first()
		;
		
		if (retValue != null)
			return retValue;
		
		//	Get CashBook
		MCashBook cb = new MCashBook (ctx, C_CashBook_ID, trxName);
		if (cb.get_ID() ==0)
		{
			s_log.warning("Not found C_CashBook_ID=" + C_CashBook_ID);
			return null;
		}
		
		//	Create New Journal
		retValue = new MCash (cb, dateAcct);
		retValue.saveEx(trxName);
		return retValue;
	}	//	get

	/**	Static Logger	*/
	private static CLogger	s_log	= CLogger.getCLogger (MCash.class);

	
	/**************************************************************************
	 * 	Standard Constructor
	 *	@param ctx context
	 *	@param C_Cash_ID id
	 *	@param trxName transaction
	 */
	public MCash (Properties ctx, int C_Cash_ID, String trxName)
	{
		super (ctx, C_Cash_ID, trxName);
		if (C_Cash_ID == 0)
		{
		//	setC_CashBook_ID (0);		//	FK
			setBeginningBalance (Env.ZERO);
			setEndingBalance (Env.ZERO);
			setStatementDifference(Env.ZERO);
			setDocAction(DOCACTION_Complete);
			setDocStatus(DOCSTATUS_Drafted);
			//
			Timestamp today = TimeUtil.getDay(System.currentTimeMillis());
			setStatementDate (today);	// @#Date@
			setDateAcct (today);	// @#Date@
			String name = DisplayType.getDateFormat(DisplayType.Date).format(today)
				+ " " + MOrg.get(ctx, getAD_Org_ID()).getValue();
			setName (name);	
			setIsApproved(false);
			setPosted (false);	// N
			setProcessed (false);
		}
	}	//	MCash

	/**
	 * 	Load Constructor
	 *	@param ctx context
	 *	@param rs result set
	 *	@param trxName transaction
	 */
	public MCash (Properties ctx, ResultSet rs, String trxName)
	{
		super(ctx, rs, trxName);
	}	//	MCash
	
	/**
	 * 	Parent Constructor
	 *	@param cb cash book
	 *	@param today date - if null today
	 */
	public MCash (MCashBook cb, Timestamp today)
	{
		this (cb.getCtx(), 0, cb.get_TrxName());
		setClientOrg(cb);
		setC_CashBook_ID(cb.getC_CashBook_ID());
		if (today != null)
		{
			setStatementDate (today);	
			setDateAcct (today);
			String name = DisplayType.getDateFormat(DisplayType.Date).format(today)
				+ " " + cb.getName();
			setName (name);	
		}
		m_book = cb;
	}	//	MCash
	
	/**	Lines					*/
	private MCashLine[]		m_lines = null;
	/** CashBook				*/
	private MCashBook		m_book = null;
	
	/**
	 * 	Get Lines
	 *	@param requery requery
	 *	@return lines
	 */
	public MCashLine[] getLines (boolean requery)
	{
		if (m_lines != null && !requery) {
			set_TrxName(m_lines, get_TrxName());
			return m_lines;
		}
		
		final String whereClause =MCashLine.COLUMNNAME_C_Cash_ID+"=?"; 
		List<MCashLine> list = new Query(getCtx(),I_C_CashLine.Table_Name,  whereClause, get_TrxName())
								.setParameters(getC_Cash_ID())
								.setOrderBy(I_C_CashLine.COLUMNNAME_Line)
								.setOnlyActiveRecords(true)
								.list();
		
		m_lines =  list.toArray(new MCashLine[list.size()]);
		return m_lines;
	}	//	getLines

	/**
	 * 	Get Cash Book
	 *	@return cash book
	 */
	public MCashBook getCashBook()
	{
		if (m_book == null)
			m_book = MCashBook.get(getCtx(), getC_CashBook_ID());
		return m_book;
	}	//	getCashBook
	
	/**
	 * 	Get Document No 
	 *	@return name
	 */
	public String getDocumentNo()
	{
		return getName();
	}	//	getDocumentNo

	/**
	 * 	Get Document Info
	 *	@return document info (untranslated)
	 */
	public String getDocumentInfo()
	{
		return Msg.getElement(getCtx(), "C_Cash_ID") + " " + getDocumentNo();
	}	//	getDocumentInfo

	/**
	 * 	Create PDF
	 *	@return File or null
	 */
	public File createPDF ()
	{
		try
		{
			File temp = File.createTempFile(get_TableName()+get_ID()+"_", ".pdf");
			return createPDF (temp);
		}
		catch (Exception e)
		{
			log.severe("Could not create PDF - " + e.getMessage());
		}
		return null;
	}	//	getPDF

	/**
	 * 	Create PDF file
	 *	@param file output file
	 *	@return file if success
	 */
	public File createPDF (File file)
	{
	//	ReportEngine re = ReportEngine.get (getCtx(), ReportEngine.INVOICE, getC_Invoice_ID());
	//	if (re == null)
			return null;
	//	return re.getPDF(file);
	}	//	createPDF

	/**
	 * 	Before Save
	 *	@param newRecord
	 *	@return true
	 */
	protected boolean beforeSave (boolean newRecord)
	{
		/**faaguilar OFB original code comented*/
		/*setAD_Org_ID(getCashBook().getAD_Org_ID());
		if (getAD_Org_ID() == 0)
		{
			log.saveError("Error", Msg.parseTranslation(getCtx(), "@AD_Org_ID@"));
			return false;
		}*/
		/**faaguilar end*/
		
		//faaguilar OFB duplicate
		if(getDocStatus().equals("DR"))
		{
			int count = DB.getSQLValue(get_TrxName(),"select count(1) from C_Cash where DocStatus IN ('DR','IP') and C_CashBook_ID="+getC_CashBook_ID()+" and C_Cash_ID!="+getC_Cash_ID());
			if(count>0)
			{
				log.saveError("Error", "Ya existe otra caja en borrador o en proceso por favor complétela antes de crear una nueva");
								m_processMsg = "Ya existe otra caja en borrador por favor complétela antes de crear una nueva";
								return false;
			}
		}
		//End validation
		//faaguilar OFB setBeginningBalance
		Calendar calCalendario = Calendar.getInstance();
        calCalendario.setTimeInMillis(getDateAcct().getTime());
        //calCalendario.add(Calendar.DATE, -1);
        Timestamp tmsFecha = new Timestamp(calCalendario.getTimeInMillis());
        /*if(OFBForward.UseOnlyCashForBBalance())//se sobreescribe fecha por posibles problemas
        	tmsFecha = getDateAcct();*/        
		//MCash oldCash= MCash.get (getCtx(), getAD_Org_ID(),tmsFecha, getC_Currency_ID(),get_TrxName());
        MCash oldCash=MCash.getDuplicate(getCtx(), getAD_Org_ID(),tmsFecha, getC_CashBook_ID(),get_TrxName(),getC_Cash_ID());
        //Se sobreescribe diario para que solo tome el ID del diario sin fecha ni org 
        if(OFBForward.UseOnlyCashForBBalance())
        	oldCash=MCash.getDuplicate(getCtx(),getC_CashBook_ID(),get_TrxName(),getC_Cash_ID());
        
        if(oldCash==null)
        	setBeginningBalance(Env.ZERO);
        else
        	setBeginningBalance(oldCash.getEndingBalance());
		//END setBeginningBalance
        if(newRecord)//ininoles si es registro nuevo que diferencia sea 0
        	setStatementDifference(Env.ZERO);
		//	Calculate End Balance
		setEndingBalance(getBeginningBalance().add(getStatementDifference()));
		return true;
	}	//	beforeSave
	
	
	/**************************************************************************
	 * 	Process document
	 *	@param processAction document action
	 *	@return true if performed
	 */
	public boolean processIt (String processAction)
	{
		m_processMsg = null;
		DocumentEngine engine = new DocumentEngine (this, getDocStatus());
		return engine.processIt (processAction, getDocAction());
	}	//	process
	
	/**	Process Message 			*/
	private String		m_processMsg = null;
	/**	Just Prepared Flag			*/
	private boolean		m_justPrepared = false;

	/**
	 * 	Unlock Document.
	 * 	@return true if success 
	 */
	public boolean unlockIt()
	{
		log.info(toString());
		setProcessing(false);
		return true;
	}	//	unlockIt
	
	/**
	 * 	Invalidate Document
	 * 	@return true if success 
	 */
	public boolean invalidateIt()
	{
		log.info(toString());
		setDocAction(DOCACTION_Prepare);
		return true;
	}	//	invalidateIt
	
	/**
	 *	Prepare Document
	 * 	@return new status (In Progress or Invalid) 
	 */
	public String prepareIt()
	{
		log.info(toString());
		m_processMsg = ModelValidationEngine.get().fireDocValidate(this, ModelValidator.TIMING_BEFORE_PREPARE);
		if (m_processMsg != null)
			return DocAction.STATUS_Invalid;

		//	Std Period open?
		if (!MPeriod.isOpen(getCtx(), getDateAcct(), MDocType.DOCBASETYPE_CashJournal, getAD_Org_ID()))
		{
			m_processMsg = "@PeriodClosed@";
			return DocAction.STATUS_Invalid;
		}
		MCashLine[] lines = getLines(false);
		if (lines.length == 0)
		{
			m_processMsg = "@NoLines@";
			return DocAction.STATUS_Invalid;
		}
		//	Add up Amounts
		BigDecimal difference = Env.ZERO;
		int C_Currency_ID = getC_Currency_ID();
		for (int i = 0; i < lines.length; i++)
		{
			MCashLine line = lines[i];
			if (!line.isActive())
				continue;
			if (C_Currency_ID == line.getC_Currency_ID())
				difference = difference.add(line.getAmount());
			else
			{
				BigDecimal amt = MConversionRate.convert(getCtx(), line.getAmount(), 
					line.getC_Currency_ID(), C_Currency_ID, getDateAcct(), 0, 
					getAD_Client_ID(), getAD_Org_ID());
				if (amt == null)
				{
					m_processMsg = "No Conversion Rate found - @C_CashLine_ID@= " + line.getLine();
					return DocAction.STATUS_Invalid;
				}
				difference = difference.add(amt);
			}
		}
		setStatementDifference(difference);
	//	setEndingBalance(getBeginningBalance().add(getStatementDifference()));

		m_processMsg = ModelValidationEngine.get().fireDocValidate(this, ModelValidator.TIMING_AFTER_PREPARE);
		if (m_processMsg != null)
			return DocAction.STATUS_Invalid;

		m_justPrepared = true;
		if (!DOCACTION_Complete.equals(getDocAction()))
			setDocAction(DOCACTION_Complete);
		return DocAction.STATUS_InProgress;
	}	//	prepareIt
	
	/**
	 * 	Approve Document
	 * 	@return true if success 
	 */
	public boolean  approveIt()
	{
		log.info(toString());
		setIsApproved(true);
		return true;
	}	//	approveIt
	
	/**
	 * 	Reject Approval
	 * 	@return true if success 
	 */
	public boolean rejectIt()
	{
		log.info(toString());
		setIsApproved(false);
		return true;
	}	//	rejectIt
	
	/**
	 * 	Complete Document
	 * 	@return new status (Complete, In Progress, Invalid, Waiting ..)
	 */
	public String completeIt()
	{
		//	Re-Check
		if (!m_justPrepared)
		{
			String status = prepareIt();
			if (!DocAction.STATUS_InProgress.equals(status))
				return status;
		}
		
		m_processMsg = ModelValidationEngine.get().fireDocValidate(this, ModelValidator.TIMING_BEFORE_COMPLETE);
		if (m_processMsg != null)
			return DocAction.STATUS_Invalid;

		
		//	Implicit Approval
		if (!isApproved())
			approveIt();
		//
		log.info(toString());
		
		MCashLine[] lines = getLines(false);
		for (int i = 0; i < lines.length; i++)
		{
			MCashLine line = lines[i];
			if (MCashLine.CASHTYPE_Invoice.equals(line.getCashType()))
			{
				// Check if the invoice is completed - teo_sarca BF [ 1894524 ]
				MInvoice invoice = line.getInvoice();
				//faaguilar OFB patch for lines voided begin
				if (MInvoice.DOCSTATUS_Voided.equals(invoice.getDocStatus()) && line.getAmount().signum()==0)
					continue;
				//faaguilar OFB patch for lines voided end
				
				if (   !MInvoice.DOCSTATUS_Completed.equals(invoice.getDocStatus())
					&& !MInvoice.DOCSTATUS_Closed.equals(invoice.getDocStatus())
					&& !MInvoice.DOCSTATUS_Reversed.equals(invoice.getDocStatus())
					&& !MInvoice.DOCSTATUS_Voided.equals(invoice.getDocStatus())
					)
				{
					m_processMsg = "@Line@ "+line.getLine()+": @InvoiceCreateDocNotCompleted@";
					return DocAction.STATUS_Invalid;
				}
				//
				String name = Msg.translate(getCtx(), "C_Cash_ID") + ": " + getName()
								+ " - " + Msg.translate(getCtx(), "Line") + " " + line.getLine();
				MAllocationHdr hdr = new MAllocationHdr(getCtx(), false, 
						getDateAcct(), line.getC_Currency_ID(),
						name, get_TrxName());
				hdr.setAD_Org_ID(getAD_Org_ID());
				if (!hdr.save())
				{
					m_processMsg = CLogger.retrieveErrorString("Could not create Allocation Hdr");
					return DocAction.STATUS_Invalid;
				}
				//	Allocation Line
				MAllocationLine aLine = new MAllocationLine (hdr, line.getAmount(),
					line.getDiscountAmt(), line.getWriteOffAmt(), Env.ZERO);
				aLine.setC_Invoice_ID(line.getC_Invoice_ID());
				aLine.setC_CashLine_ID(line.getC_CashLine_ID());
				if (!aLine.save())
				{
					m_processMsg = CLogger.retrieveErrorString("Could not create Allocation Line");
					return DocAction.STATUS_Invalid;
				}
				//	Should start WF
				if(!hdr.processIt(DocAction.ACTION_Complete)) {
					m_processMsg = CLogger.retrieveErrorString("Could not process Allocation");
					return DocAction.STATUS_Invalid;
				}
				if (!hdr.save()) {
					m_processMsg = CLogger.retrieveErrorString("Could not save Allocation");
					return DocAction.STATUS_Invalid;
				}
			}
			else if (MCashLine.CASHTYPE_BankAccountTransfer.equals(line.getCashType()))
			{
				if (line.getC_Payment_ID()<=0){ //added by faaguilar OFB
					//	Payment just as intermediate info
					MPayment pay = new MPayment (getCtx(), 0, get_TrxName());
					pay.setAD_Org_ID(getAD_Org_ID());
					String documentNo = getName();
					pay.setDocumentNo(documentNo);
					pay.setR_PnRef(documentNo);
					
					pay.set_ValueNoCheck("TrxType", "X");//faaguilar OFB
					
					//
					//Modification for cash payment - Posterita
					pay.setC_CashBook_ID(getC_CashBook_ID());
					//End of modification - Posterita
					
					pay.setC_BankAccount_ID(line.getC_BankAccount_ID());
					pay.setC_DocType_ID(true);	//	Receipt
					pay.setDateTrx(getStatementDate());
					pay.setDateAcct(getDateAcct());
					pay.setAmount(line.getC_Currency_ID(), line.getAmount().negate());	//	Transfer
					pay.setDescription(line.getDescription());
					pay.setDocStatus(MPayment.DOCSTATUS_Closed);
					pay.setDocAction(MPayment.DOCACTION_None);
					pay.setPosted(true);
					pay.setIsAllocated(true);	//	Has No Allocation!
					pay.setProcessed(true);		
					
					/**faaguilar OFB setting for payment transfer begin*/
					if(line.getAmount().compareTo(Env.ZERO)>0)
						pay.setC_DocType_ID(false);
					else
						pay.setC_DocType_ID(true);
					pay.setC_BPartner_ID(line.get_ValueAsInt("C_BPartner_ID"));
					pay.setTenderType(line.get_ValueAsString("TenderType"));
					if(line.get_ValueAsString("TenderType").equals("K"))
						pay.setCheckNo(line.get_ValueAsString("DocumentNo"));
					else{
						pay.setDocumentNo(line.get_ValueAsString("DocumentNo"));
						pay.setR_PnRef(line.get_ValueAsString("DocumentNo"));
					}
					pay.setAmount(line.getC_Currency_ID(), line.getAmount().abs());
					
					/**faaguilar OFB setting for payment transfer end*/
					if (!pay.save())
					{
						m_processMsg = CLogger.retrieveErrorString("Could not create Payment");
						return DocAction.STATUS_Invalid;
					}
					
					line.setC_Payment_ID(pay.getC_Payment_ID());
					if (!line.save())
					{
						m_processMsg = "Could not update Cash Line";
						return DocAction.STATUS_Invalid;
					}
				}	
			}
		}
		
		//	User Validation
		String valid = ModelValidationEngine.get().fireDocValidate(this, ModelValidator.TIMING_AFTER_COMPLETE);
		if (valid != null)
		{
			m_processMsg = valid;
			return DocAction.STATUS_Invalid;
		}
		//
		setProcessed(true);
		setDocAction(DOCACTION_Close);
		return DocAction.STATUS_Completed;
	}	//	completeIt
	
	/**
	 * 	Void Document.
	 * 	Same as Close.
	 * 	@return true if success 
	 */
	public boolean voidIt()
	{
		log.info(toString());
		// Before Void
		m_processMsg = ModelValidationEngine.get().fireDocValidate(this,ModelValidator.TIMING_BEFORE_VOID);
		if (m_processMsg != null)
			return false;

		//FR [ 1866214 ]
		boolean retValue = reverseIt();
		
		if (retValue) {
			// After Void
			m_processMsg = ModelValidationEngine.get().fireDocValidate(this,ModelValidator.TIMING_AFTER_VOID);
			if (m_processMsg != null)
				return false;		
			setDocAction(DOCACTION_None);
		}

		return retValue;
	}	//	voidIt
	
	//FR [ 1866214 ]
	/**************************************************************************
	 * 	Reverse Cash
	 * 	Period needs to be open
	 *	@return true if reversed
	 */
	private boolean reverseIt() 
	{
		if (DOCSTATUS_Closed.equals(getDocStatus())
			|| DOCSTATUS_Reversed.equals(getDocStatus())
			|| DOCSTATUS_Voided.equals(getDocStatus()))
		{
			m_processMsg = "Document Closed: " + getDocStatus();
			setDocAction(DOCACTION_None);
			return false;
		}

		//	Can we delete posting
		if (!MPeriod.isOpen(getCtx(), this.getDateAcct(), MPeriodControl.DOCBASETYPE_CashJournal, getAD_Org_ID()))
			throw new IllegalStateException("@PeriodClosed@");
		
		//	Reverse Allocations
		MAllocationHdr[] allocations = MAllocationHdr.getOfCash(getCtx(), getC_Cash_ID(), get_TrxName());
		for(MAllocationHdr allocation : allocations)
		{
			allocation.reverseCorrectIt();
			if(!allocation.save())
				throw new IllegalStateException("Cannot reverse allocations");
		}	

		MCashLine[] cashlines = getLines(true);
		for (MCashLine cashline : cashlines )
		{
			BigDecimal oldAmount = cashline.getAmount();
			BigDecimal oldDiscount = cashline.getDiscountAmt();
			BigDecimal oldWriteOff = cashline.getWriteOffAmt();
			cashline.setAmount(Env.ZERO);
			cashline.setDiscountAmt(Env.ZERO);
			cashline.setWriteOffAmt(Env.ZERO);
			cashline.addDescription(Msg.getMsg(getCtx(), "Voided")
					+ " (Amount=" + oldAmount + ", Discount=" + oldDiscount
					+ ", WriteOff=" + oldWriteOff + ", )");
			if (MCashLine.CASHTYPE_BankAccountTransfer.equals(cashline.getCashType()))
			{
				if (cashline.getC_Payment_ID() == 0)
					throw new IllegalStateException("Cannot reverse payment");
					
				MPayment payment = new MPayment(getCtx(), cashline.getC_Payment_ID(),get_TrxName());
				if (payment.getC_BankStatementLine2_ID() < 1)
				{
					payment.reverseCorrectIt();
					payment.saveEx();
				}
				else
				{
					throw new IllegalStateException("Pago Tiene un Estado de Cuentas Bancario Asociado");
				}
				
			}
			cashline.saveEx();
		}
		
		setName(getName()+"^");
		addDescription(Msg.getMsg(getCtx(), "Voided"));
		setDocStatus(DOCSTATUS_Reversed);	//	for direct calls
		setProcessed(true);
		setPosted(true);
		setDocAction(DOCACTION_None);
		saveEx();
			
		//	Delete Posting
		MFactAcct.deleteEx(Table_ID, getC_Cash_ID(), get_TrxName());
		
		return true;
	}	//	reverse
	
	/**
	 * 	Add to Description
	 *	@param description text
	 */
	public void addDescription (String description)
	{
		String desc = getDescription();
		if (desc == null)
			setDescription(description);
		else
			setDescription(desc + " | " + description);
	}	//	addDescription

	/**
	 * 	Close Document.
	 * 	Cancel not delivered Quantities
	 * 	@return true if success 
	 */
	public boolean closeIt()
	{
		log.info(toString());
		// Before Close
		m_processMsg = ModelValidationEngine.get().fireDocValidate(this,ModelValidator.TIMING_BEFORE_CLOSE);
		if (m_processMsg != null)
			return false;
		
		setDocAction(DOCACTION_None);

		// After Close
		m_processMsg = ModelValidationEngine.get().fireDocValidate(this,ModelValidator.TIMING_AFTER_CLOSE);
		if (m_processMsg != null)
			return false;
		return true;
	}	//	closeIt
	
	/**
	 * 	Reverse Correction
	 * 	@return true if success 
	 */
	public boolean reverseCorrectIt()
	{
		log.info(toString());
		// Before reverseCorrect
		m_processMsg = ModelValidationEngine.get().fireDocValidate(this,ModelValidator.TIMING_BEFORE_REVERSECORRECT);
		if (m_processMsg != null)
			return false;
		
		//FR [ 1866214 ]
		boolean retValue = reverseIt();
		
		if (retValue) {
			// After reverseCorrect
			m_processMsg = ModelValidationEngine.get().fireDocValidate(this,ModelValidator.TIMING_AFTER_REVERSECORRECT);
			if (m_processMsg != null)
				return false;		
		}
		
		return retValue;
	}	//	reverseCorrectionIt
	
	/**
	 * 	Reverse Accrual - none
	 * 	@return true if success 
	 */
	public boolean reverseAccrualIt()
	{
		log.info(toString());
		// Before reverseAccrual
		m_processMsg = ModelValidationEngine.get().fireDocValidate(this,ModelValidator.TIMING_BEFORE_REVERSEACCRUAL);
		if (m_processMsg != null)
			return false;
		
		// After reverseAccrual
		m_processMsg = ModelValidationEngine.get().fireDocValidate(this,ModelValidator.TIMING_AFTER_REVERSEACCRUAL);
		if (m_processMsg != null)
			return false;
				
		return false;
	}	//	reverseAccrualIt
	
	/** 
	 * 	Re-activate
	 * 	@return true if success 
	 */
	public boolean reActivateIt()
	{
		log.info(toString());
		// Before reActivate
		m_processMsg = ModelValidationEngine.get().fireDocValidate(this,ModelValidator.TIMING_BEFORE_REACTIVATE);
		if (m_processMsg != null)
			return false;	
				
		setProcessed(false);
		if (reverseCorrectIt())
			return true;

		// After reActivate
		m_processMsg = ModelValidationEngine.get().fireDocValidate(this,ModelValidator.TIMING_AFTER_REACTIVATE);
		if (m_processMsg != null)
			return false;		
		return false;
	}	//	reActivateIt
	
	/**
	 * 	Set Processed
	 *	@param processed processed
	 */
	public void setProcessed (boolean processed)
	{
		super.setProcessed (processed);
		String sql = "UPDATE C_CashLine SET Processed='"
			+ (processed ? "Y" : "N")
			+ "' WHERE C_Cash_ID=" + getC_Cash_ID();
		int noLine = DB.executeUpdate (sql, get_TrxName());
		m_lines = null;
		log.fine(processed + " - Lines=" + noLine);
	}	//	setProcessed
	
	/**
	 * 	String Representation
	 *	@return info
	 */
	public String toString ()
	{
		StringBuffer sb = new StringBuffer ("MCash[");
		sb.append (get_ID ())
			.append ("-").append (getName())
			.append(", Balance=").append(getBeginningBalance())
			.append("->").append(getEndingBalance())
			.append ("]");
		return sb.toString ();
	}	//	toString
	
	/*************************************************************************
	 * 	Get Summary
	 *	@return Summary of Document
	 */
	public String getSummary()
	{
		StringBuffer sb = new StringBuffer();
		sb.append(getName());
		//	: Total Lines = 123.00 (#1)
		sb.append(": ")
			.append(Msg.translate(getCtx(),"BeginningBalance")).append("=").append(getBeginningBalance())
			.append(",")
			.append(Msg.translate(getCtx(),"EndingBalance")).append("=").append(getEndingBalance())
			.append(" (#").append(getLines(false).length).append(")");
		//	 - Description
		if (getDescription() != null && getDescription().length() > 0)
			sb.append(" - ").append(getDescription());
		return sb.toString();
	}	//	getSummary
	
	/**
	 * 	Get Process Message
	 *	@return clear text error message
	 */
	public String getProcessMsg()
	{
		return m_processMsg;
	}	//	getProcessMsg
	
	/**
	 * 	Get Document Owner (Responsible)
	 *	@return AD_User_ID
	 */
	public int getDoc_User_ID()
	{
		return getCreatedBy();
	}	//	getDoc_User_ID

	/**
	 * 	Get Document Approval Amount
	 *	@return amount difference
	 */
	public BigDecimal getApprovalAmt()
	{
		return getStatementDifference();
	}	//	getApprovalAmt

	/**
	 * 	Get Currency
	 *	@return Currency
	 */
	public int getC_Currency_ID ()
	{
		return getCashBook().getC_Currency_ID();
	}	//	getC_Currency_ID

	/**
	 * 	Document Status is Complete or Closed
	 *	@return true if CO, CL or RE
	 */
	public boolean isComplete()
	{
		String ds = getDocStatus();
		return DOCSTATUS_Completed.equals(ds) 
			|| DOCSTATUS_Closed.equals(ds)
			|| DOCSTATUS_Reversed.equals(ds);
	}	//	isComplete
	
	/** faaguilar OFB 
	 * 	trae el cashbook por defecto configurado para el cliente
	 *	@param ctx context
	 *	@param AD_Client_ID client
	 *	@param trxName transaction
	 *	@return cash
	 */
	public static MCash getDefault (Properties ctx, int AD_Client_ID, 
		int C_CashBook_ID, String trxName)
	{
		MCash retValue = null;
		//	Existing Journal
		String sql = "SELECT * FROM C_Cash c "
			+ "WHERE c.AD_Client_ID=?"					//	#1
			+ " And c.C_CashBook_ID="+C_CashBook_ID
			+ " AND c.Processed='N' ";
		PreparedStatement pstmt = null;
		try
		{
			pstmt = DB.prepareStatement (sql, trxName);
			pstmt.setInt (1, AD_Client_ID);
			ResultSet rs = pstmt.executeQuery ();
			if (rs.next ())
				retValue = new MCash (ctx, rs, trxName);
			rs.close ();
			pstmt.close ();
			pstmt = null;
		}
		catch (Exception e)
		{
			s_log.log(Level.SEVERE, sql, e);
		}
		try
		{
			if (pstmt != null)
				pstmt.close ();
			pstmt = null;
		}
		catch (Exception e)
		{
			pstmt = null;
		}
		
		
			return retValue;
		
		
	}	//	get
	
	/** faaguilar OFB method for cash duplicate
	 * 	Get Cash Journal for CashBook and date
	 *	@param ctx context
	 *	@param AD_Client_ID client
	 *	@param dateAcct date
	 *	@param trxName transaction
	 *	@return cash
	 */
	public static MCash getDuplicate (Properties ctx, int AD_Org_ID, 
		Timestamp dateAcct, int CashBook_ID, String trxName, int C_Cash_ID)
	{
		MCash retValue = null;
		//	Existing Journal
		String sql = "SELECT * FROM C_Cash c "
			+ "WHERE c.AD_Org_ID=?"					//	#1
			+ " AND TRUNC(c.StatementDate)<=?"    //	#2
			+ " AND c.C_CashBook_ID=?"			 //	#3
			+ " AND c.DocStatus IN ('DR','CO')"
			+ " AND c.C_CASH_ID!="+C_Cash_ID
			+ " Order BY c.StatementDate Desc,c.created Desc";
		PreparedStatement pstmt = null;
		try
		{
			pstmt = DB.prepareStatement (sql, trxName);
			pstmt.setInt (1, AD_Org_ID);
			pstmt.setTimestamp (2, TimeUtil.getDay(dateAcct));
			pstmt.setInt (3, CashBook_ID);
			ResultSet rs = pstmt.executeQuery ();
			if (rs.next ())
				retValue = new MCash (ctx, rs, trxName);
			else 
				retValue = getDuplicate (ctx, dateAcct,  CashBook_ID, trxName, C_Cash_ID);
			rs.close ();
			pstmt.close ();
			pstmt = null;
		}
		catch (Exception e)
		{
			s_log.log(Level.SEVERE, sql, e);
		}
		try
		{
			if (pstmt != null)
				pstmt.close ();
			pstmt = null;
		}
		catch (Exception e)
		{
			pstmt = null;
		}
		
		
			return retValue;
		
		
	}	//	get
	
	/** faaguilar OFB method for cash duplicate NO ORG
	 * 	Get Cash Journal for CashBook and date
	 *	@param ctx context
	 *	@param AD_Client_ID client
	 *	@param dateAcct date
	 *	@param trxName transaction
	 *	@return cash
	 */
	public static MCash getDuplicate (Properties ctx, 
		Timestamp dateAcct, int CashBook_ID, String trxName, int C_Cash_ID)
	{
		MCash retValue = null;
		//	Existing Journal
		String sql = "SELECT * FROM C_Cash c "
			+ "WHERE TRUNC(c.StatementDate)<=?"    //	#2
			+ " AND c.C_CashBook_ID=?"			 //	#3
			+ " AND c.DocStatus IN ('DR','CO')"
			+ " AND c.C_CASH_ID!="+C_Cash_ID
			+ " Order BY c.StatementDate Desc,c.created Desc";
		PreparedStatement pstmt = null;
		try
		{
			pstmt = DB.prepareStatement (sql, trxName);
			pstmt.setTimestamp (1, TimeUtil.getDay(dateAcct));
			pstmt.setInt (2, CashBook_ID);
			ResultSet rs = pstmt.executeQuery ();
			if (rs.next ())
				retValue = new MCash (ctx, rs, trxName);
			rs.close ();
			pstmt.close ();
			pstmt = null;
		}
		catch (Exception e)
		{
			s_log.log(Level.SEVERE, sql, e);
		}
		try
		{
			if (pstmt != null)
				pstmt.close ();
			pstmt = null;
		}
		catch (Exception e)
		{
			pstmt = null;
		}
		
		
			return retValue;
		
		
	}	//	get
	/** faaguilar OFB method for cash duplicate NO ORG
	 * 	Get Cash Journal for CashBook and date
	 *	@param ctx context
	 *	@param AD_Client_ID client
	 *	@param dateAcct date
	 *	@param trxName transaction
	 *	@return cash
	 */
	public static MCash getDuplicate (Properties ctx, 
		int CashBook_ID, String trxName, int C_Cash_ID)
	{
		MCash retValue = null;
		//	Existing Journal
		String sql = "SELECT * FROM C_Cash c "
			+ "WHERE c.C_CashBook_ID=?"			 //	#3
			+ " AND c.DocStatus IN ('DR','CO')"
			+ " AND c.C_CASH_ID!="+C_Cash_ID
			//+ " Order BY c.StatementDate Desc,c.created Desc";
			+ " Order BY c.documentNo desc, c.StatementDate Desc,c.created Desc";
		PreparedStatement pstmt = null;
		try
		{
			pstmt = DB.prepareStatement (sql, trxName);			
			pstmt.setInt (1, CashBook_ID);
			ResultSet rs = pstmt.executeQuery ();
			if (rs.next ())
				retValue = new MCash (ctx, rs, trxName);
			rs.close ();
			pstmt.close ();
			pstmt = null;
		}
		catch (Exception e)
		{
			s_log.log(Level.SEVERE, sql, e);
		}
		try
		{
			if (pstmt != null)
				pstmt.close ();
			pstmt = null;
		}
		catch (Exception e)
		{
			pstmt = null;
		}
		
		
			return retValue;
	}
	
}	//	MCash
