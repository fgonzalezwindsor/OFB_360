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
package org.petroamerica.model;

import org.compiere.model.MBPartner;
import org.compiere.model.MClient;
import org.compiere.model.MInvoice;
import org.compiere.model.MOrder;
import org.compiere.model.MPayment;
import org.compiere.model.ModelValidationEngine;
import org.compiere.model.ModelValidator;
import org.compiere.model.PO;
import org.compiere.util.CLogger;
import org.compiere.util.DB;
import org.compiere.util.Env;


/**
 *	Validator for PA
 *
 *  @author Italo Ni�oles
 */
public class ModPAUpdateCreditBPAD implements ModelValidator
{
	/**
	 *	Constructor.
	 *	The class is instantiated when logging in and client is selected/known
	 */
	public ModPAUpdateCreditBPAD ()
	{
		super ();
	}	//	MyValidator

	/**	Logger			*/
	private static CLogger log = CLogger.getCLogger(ModPAUpdateCreditBPAD.class);
	/** Client			*/
	private int		m_AD_Client_ID = -1;
	

	/**
	 *	Initialize Validation
	 *	@param engine validation engine
	 *	@param client client
	 */
	public void initialize (ModelValidationEngine engine, MClient client)
	{
		//client = null for global validator
		if (client != null) {
			m_AD_Client_ID = client.getAD_Client_ID();
			log.info(client.toString());
		}
		else  {
			log.info("Initializing Model Price Validator: "+this.toString());
		}
		//doc to be monitored
		engine.addModelChange(MBPartner.Table_Name, this);
		//engine.addDocValidate(MInOut.Table_Name, this);
		engine.addDocValidate(MOrder.Table_Name, this);
		engine.addDocValidate(MPayment.Table_Name, this);
		engine.addDocValidate(MInvoice.Table_Name, this);		
		
		
	}	//	initialize

    /**
     *	Model Change of a monitored Table.
     *	OFB Consulting Ltda. By italo ni�oles
     */
	public String modelChange (PO po, int type) throws Exception
	{
		log.info(po.get_TableName() + " Type: "+type);
		if(type == TYPE_AFTER_CHANGE && po.get_Table_ID()==MBPartner.Table_ID)
		{
			MBPartner bp = (MBPartner)po;
			DB.executeUpdate("UPDATE C_BPartner SET SO_CreditLimit = 0, SO_CreditUsed = 0, " +
					" TotalOpenBalance = 0 WHERE C_BPartner_ID = "+bp.get_ID(), po.get_TrxName());
		}
		
		return null;
	}	//	modelChange

	public String docValidate (PO po, int timing)
	{
		log.info(po.get_TableName() + " Timing: "+timing);		
		if(timing == TIMING_AFTER_COMPLETE && po.get_Table_ID()==MOrder.Table_ID) 
		{	
			MOrder order = (MOrder)po;
			if(order.isSOTrx())
			{
				MBPartner bp = new MBPartner(po.getCtx(), order.getC_BPartner_ID(), po.get_TrxName());
				bp.setSO_CreditLimit(Env.ZERO);
				bp.setSO_CreditUsed(Env.ZERO);
				bp.setTotalOpenBalance(Env.ZERO);
				bp.save();	
			}	
		}
		if(timing == TIMING_AFTER_COMPLETE && po.get_Table_ID()==MPayment.Table_ID) 
		{	
			MPayment pay = (MPayment)po;
			if(pay.isReceipt())
			{
				MBPartner bp = new MBPartner(po.getCtx(), pay.getC_BPartner_ID(), po.get_TrxName());
				bp.setSO_CreditLimit(Env.ZERO);
				bp.setSO_CreditUsed(Env.ZERO);
				bp.setTotalOpenBalance(Env.ZERO);
				bp.save();	
			}
		}
		if(timing == TIMING_AFTER_COMPLETE && po.get_Table_ID()==MInvoice.Table_ID) 
		{	
			MInvoice inv = (MInvoice)po;
			MBPartner bp = new MBPartner(po.getCtx(), inv.getC_BPartner_ID(), po.get_TrxName());
			bp.setSO_CreditLimit(Env.ZERO);
			bp.setSO_CreditUsed(Env.ZERO);
			bp.setTotalOpenBalance(Env.ZERO);
			bp.save();	
		}
		return null;
	}	//	docValidate
	
	/**
	 *	User Login.
	 *	Called when preferences are set
	 *	@param AD_Org_ID org
	 *	@param AD_Role_ID role
	 *	@param AD_User_ID user
	 *	@return error message or null
	 */
	public String login (int AD_Org_ID, int AD_Role_ID, int AD_User_ID)
	{
		log.info("AD_User_ID=" + AD_User_ID);

		return null;
	}	//	login


	/**
	 *	Get Client to be monitored
	 *	@return AD_Client_ID client
	 */
	public int getAD_Client_ID()
	{
		return m_AD_Client_ID;
	}	//	getAD_Client_ID


	/**
	 * 	String Representation
	 *	@return info
	 */
	public String toString ()
	{
		StringBuffer sb = new StringBuffer ("ModelPrice");
		return sb.toString ();
	}	//	toString


	

}	
